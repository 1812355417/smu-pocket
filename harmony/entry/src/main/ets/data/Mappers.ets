import { Course, Grade } from '../core/Models';
import { LocalDate } from '../core/LocalDate';
import { clockRange, dateOf, parseWeekList, sectionsOf } from '../core/TimeUtils';
import { CourseData, creditOf, gradePointOf, ScoreData, scoreOf } from './JwModels';

/**
 * 接口模型 → 领域模型（对应 Android 端 data/model/Mappers.kt）。
 *
 * UI 只认 Course / Grade，不认教务的 40 个字段。好处：
 * - 教务改字段名，只改这一个文件；
 * - 隐私字段（xsbh 学号、xsxm 姓名）根本进不了领域模型。
 */

/**
 * @param fallbackWeek 调用方知道的周次。按日期/周次查询的响应里**没有 zc**，
 *   必须靠它兜底，否则 weeks 为空 → 整门课被静默丢掉。
 */
export function toCourse(data: CourseData, fallbackWeek: number | null = null,
  firstMonday: LocalDate | null = null): Course | null {
  const day = data.xq;
  if (day < 1 || day > 7) {
    return null;
  }

  const sections = sectionsOf(data);
  if (sections === null) {
    return null;
  }

  const weeksFromData = parseWeekList(data.zc);
  let weeks: Set<number>;
  if (weeksFromData.size > 0) {
    weeks = weeksFromData;
  } else if (fallbackWeek !== null) {
    weeks = new Set<number>([fallbackWeek]);
  } else {
    return null;
  }

  const name = (data.kcmc ?? '').trim();
  if (name.length === 0) {
    return null;
  }

  const course = new Course();
  const idParts: string[] = [];
  if (data.kcdm !== null) {
    idParts.push(data.kcdm);
  }
  if (data.kcbh !== null) {
    idParts.push(data.kcbh);
  }
  idParts.push(`${day}`);
  idParts.push(`${sections[0]}`);
  idParts.push(data.zc.length > 0 ? data.zc : `w${fallbackWeek}`);
  course.id = idParts.join('_');

  course.name = name;
  course.teacher = blankToNull(data.teaxms);
  course.room = blankToNull(data.jxcdmc);
  course.dayOfWeek = day;
  course.startSection = sections[0];
  course.endSection = sections[1];
  course.weeks = weeks;
  course.content = blankToNull(data.sknrjj);

  // 整学期总表没有 qsrq；但若这行只落在某一周，可以用第一周周一推出来
  const parsedDate = LocalDate.parse(data.qsrq);
  if (parsedDate !== null) {
    course.date = parsedDate;
  } else if (firstMonday !== null && weeks.size === 1) {
    const only = Array.from(weeks)[0];
    course.date = dateOf(firstMonday, only, day);
  } else {
    course.date = null;
  }

  course.timeLabel = clockRange(data);
  course.colorHex = blankToNull(data.bgcolor);
  course.isSelfStudy = isSelfStudyKind(data.jxhjmc);
  course.isExam = isExamKind(data.jxhjmc) || isExamKind(data.khfsmc);
  return course;
}

function blankToNull(v: string | null): string | null {
  if (v === null) {
    return null;
  }
  const t = v.trim();
  return t.length > 0 ? v : null;
}

/** 考试环节：「考试（理论）」「考试（实操）」；顺带兼容 khfsmc 里的「考试」 */
function isExamKind(v: string | null): boolean {
  return v !== null && v.indexOf('考试') >= 0;
}

/**
 * 自主学习判断。实测 118 行真实数据里 jxhjmc 只有 7 种取值，
 * 「自主学习」19 行**全部没有教室**，与其它类别完全可分。
 * 同时把「线上 / 网络 / 慕课 / 在线 / 自习」也算进来，将来教务改叫法也不会漏。
 */
function isSelfStudyKind(jxhjmc: string | null): boolean {
  const v = (jxhjmc ?? '').trim();
  if (v.length === 0) {
    return false;
  }
  const keys: string[] = ['自主', '线上', '网络', '慕课', '在线', '自习'];
  return keys.some((k) => v.indexOf(k) >= 0);
}

export function toGrade(data: ScoreData): Grade | null {
  const name = (data.kcmc ?? '').trim();
  if (name.length === 0) {
    return null;
  }

  const numericScore = scoreOf(data);
  // 等级制课程（优/良/中）没有分数，等级就藏在 zcj 里
  const level = numericScore === null && data.zcj.trim().length > 0 ? data.zcj.trim() : null;

  const grade = new Grade();
  grade.course = name;
  grade.term = data.xnxqmc ?? '';
  grade.score = numericScore;
  grade.level = level;
  grade.gpa = gradePointOf(data);
  grade.credit = creditOf(data);
  grade.kind = blankToNull(data.xdfsmc);
  return grade;
}
