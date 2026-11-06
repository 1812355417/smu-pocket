/**
 * 教务 JSON 的原始行模型 + 「柔性」字段转换。
 *
 * 同一个字段在不同请求里类型不一样（zc 可能是 "12,13" 也可能是数字 8；
 * zcjfs 可能是数字 88.2 也可能是字符串 "88.20"），所以这里集中做类型容错，
 * 相当于 Android 端 FlexibleStringSerializer / FlexibleIntSerializer 的作用。
 */

/** 把任意 JSON 值柔性转成字符串：null/缺失 → ""，数字/布尔 → 文本 */
export function flexString(v: Object | null | undefined): string {
  if (v === null || v === undefined) {
    return '';
  }
  if (typeof v === 'string') {
    return v;
  }
  if (typeof v === 'number' || typeof v === 'boolean') {
    return String(v);
  }
  return '';
}

/** 柔性字符串，但保留 null 语义（字段缺失与空串要区分开时用） */
export function flexStringOrNull(v: Object | null | undefined): string | null {
  if (v === null || v === undefined) {
    return null;
  }
  if (typeof v === 'string') {
    return v;
  }
  if (typeof v === 'number' || typeof v === 'boolean') {
    return String(v);
  }
  return null;
}

/** 柔性整数：数字、数字字符串、小数字符串都能吃，转不动给 0 */
export function flexInt(v: Object | null | undefined): number {
  const raw = flexString(v).trim();
  if (raw.length === 0) {
    return 0;
  }
  const i = Number(raw);
  if (!Number.isFinite(i)) {
    return 0;
  }
  return Math.trunc(i);
}

/** 取 JSON 对象的某个字段值 */
export function field(v: Object | null | undefined, key: string): Object | null {
  if (v !== null && v !== undefined && typeof v === 'object' && !Array.isArray(v)) {
    return (v as Record<string, Object>)[key] ?? null;
  }
  return null;
}

/** 是不是普通 JSON 对象 */
export function isObject(v: Object | null | undefined): boolean {
  return v !== null && v !== undefined && typeof v === 'object' && !Array.isArray(v);
}

/** 是不是 JSON 数组 */
export function asArray(v: Object | null | undefined): Object[] | null {
  if (Array.isArray(v)) {
    return v as Object[];
  }
  return null;
}

// ---------------------------------------------------------------------
// 课表行
// ---------------------------------------------------------------------

/**
 * 一条课次。字段含义见 smu-probe/字段字典-自动生成.md。
 * 整学期总表与按周查询返回的字段不一样，全部可空，由上层统一兜底。
 */
export interface CourseData {
  /** 课程名称 */
  kcmc: string | null;
  /** 课程代码 */
  kcdm: string | null;
  /** 课序号 */
  kcbh: string | null;
  /** 教师姓名（可能多个，逗号分隔） */
  teaxms: string | null;
  /** 教学场地名称 */
  jxcdmc: string | null;
  /** 教学班名称 */
  jxbmc: string | null;
  /** 讲合班形式，如「理论」「自主学习」「考试（理论）」 */
  jxhjmc: string | null;
  /** 考核方式名称（实测不返回，保留兼容） */
  khfsmc: string | null;
  /** 星期几，1=周一 … 7=周日 */
  xq: number;
  /** 周次："8"、"12,13" 或数字 8；按日期查询可能整个字段缺失 */
  zc: string;
  /** 起始节，如 "08" */
  ps: string | null;
  /** 结束节，如 "04"（仅整学期总表返回） */
  pe: string | null;
  /** 节次串，如 "0809"（仅按周查询返回） */
  jcdm: string | null;
  /** 上课日期 "2026-10-23"（仅按周查询返回）→ 反推第一周周一就靠它 */
  qsrq: string | null;
  /** 上课内容（仅按周查询返回） */
  sknrjj: string | null;
  /** 类型标记 */
  lx: string | null;
  /** 上课开始时刻 "09:50:00" */
  qssj: string | null;
  /** 上课结束时刻 "11:15:00" */
  jssj: string | null;
  /** 学期代码 "202601" */
  xnxqdm: string | null;
  /** 课表配色，如 "#1f86c7" */
  bgcolor: string | null;
  xs: number;
  pkrs: number;
}

export function courseDataFrom(v: Object | null | undefined): CourseData {
  return {
    kcmc: flexStringOrNull(field(v, 'kcmc')),
    kcdm: flexStringOrNull(field(v, 'kcdm')),
    kcbh: flexStringOrNull(field(v, 'kcbh')),
    teaxms: flexStringOrNull(field(v, 'teaxms')),
    jxcdmc: flexStringOrNull(field(v, 'jxcdmc')),
    jxbmc: flexStringOrNull(field(v, 'jxbmc')),
    jxhjmc: flexStringOrNull(field(v, 'jxhjmc')),
    khfsmc: flexStringOrNull(field(v, 'khfsmc')),
    xq: flexInt(field(v, 'xq')),
    zc: flexString(field(v, 'zc')),
    ps: flexStringOrNull(field(v, 'ps')),
    pe: flexStringOrNull(field(v, 'pe')),
    jcdm: flexStringOrNull(field(v, 'jcdm')),
    qsrq: flexStringOrNull(field(v, 'qsrq')),
    sknrjj: flexStringOrNull(field(v, 'sknrjj')),
    lx: flexStringOrNull(field(v, 'lx')),
    qssj: flexStringOrNull(field(v, 'qssj')),
    jssj: flexStringOrNull(field(v, 'jssj')),
    xnxqdm: flexStringOrNull(field(v, 'xnxqdm')),
    bgcolor: flexStringOrNull(field(v, 'bgcolor')),
    xs: flexInt(field(v, 'xs')),
    pkrs: flexInt(field(v, 'pkrs')),
  };
}

// ---------------------------------------------------------------------
// 成绩行
// ---------------------------------------------------------------------

export interface ScoreData {
  kcmc: string | null;
  kcywmc: string | null;
  kcdm: string | null;
  /** 学期代码 "202502" */
  xnxqdm: string | null;
  /** 学期名称 "2025-2026-2" */
  xnxqmc: string | null;
  /** 总成绩字符串 "88.20"（等级制课程是「优秀」） */
  zcj: string;
  /** 总成绩分数，实测为数字 88.2 */
  zcjfs: string;
  /** 绩点 */
  cjjd: string;
  /** 学分 */
  xf: string;
  /** 总学时 */
  zxs: string;
  kcdlmc: string | null;
  kcflmc: string | null;
  /** 修读方式名称，如「必修」 */
  xdfsmc: string | null;
  ksxzmc: string | null;
  cjfsmc: string | null;
  kkbmmc: string | null;
  bz: string | null;
  ismax: string | null;
  isactive: string | null;
}

export function scoreDataFrom(v: Object | null | undefined): ScoreData {
  return {
    kcmc: flexStringOrNull(field(v, 'kcmc')),
    kcywmc: flexStringOrNull(field(v, 'kcywmc')),
    kcdm: flexStringOrNull(field(v, 'kcdm')),
    xnxqdm: flexStringOrNull(field(v, 'xnxqdm')),
    xnxqmc: flexStringOrNull(field(v, 'xnxqmc')),
    zcj: flexString(field(v, 'zcj')),
    zcjfs: flexString(field(v, 'zcjfs')),
    cjjd: flexString(field(v, 'cjjd')),
    xf: flexString(field(v, 'xf')),
    zxs: flexString(field(v, 'zxs')),
    kcdlmc: flexStringOrNull(field(v, 'kcdlmc')),
    kcflmc: flexStringOrNull(field(v, 'kcflmc')),
    xdfsmc: flexStringOrNull(field(v, 'xdfsmc')),
    ksxzmc: flexStringOrNull(field(v, 'ksxzmc')),
    cjfsmc: flexStringOrNull(field(v, 'cjfsmc')),
    kkbmmc: flexStringOrNull(field(v, 'kkbmmc')),
    bz: flexStringOrNull(field(v, 'bz')),
    ismax: flexStringOrNull(field(v, 'ismax')),
    isactive: flexStringOrNull(field(v, 'isactive')),
  };
}

/** 数值型成绩。优先 zcjfs（精度更规范），退回 zcj */
export function scoreOf(s: ScoreData): number | null {
  const a = s.zcjfs.trim();
  if (a.length > 0) {
    const n = Number(a);
    if (Number.isFinite(n)) {
      return n;
    }
  }
  const b = s.zcj.trim();
  if (b.length > 0) {
    const n = Number(b);
    if (Number.isFinite(n)) {
      return n;
    }
  }
  return null;
}

export function gradePointOf(s: ScoreData): number | null {
  const raw = s.cjjd.trim();
  if (raw.length === 0) {
    return null;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

export function creditOf(s: ScoreData): number {
  const raw = s.xf.trim();
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}

// ---------------------------------------------------------------------
// 课程任务（考试）行
// ---------------------------------------------------------------------

export interface ExamTaskData {
  /** 抓包里的名字（部分响应给这个） */
  xkh: string;
  /** 实测真正的网考号字段 */
  xid: string;
  kcmc: string | null;
  khfsmc: string | null;
}

export function examTaskDataFrom(v: Object | null | undefined): ExamTaskData {
  return {
    xkh: flexString(field(v, 'xkh')),
    xid: flexString(field(v, 'xid')),
    kcmc: flexStringOrNull(field(v, 'kcmc')),
    khfsmc: flexStringOrNull(field(v, 'khfsmc')),
  };
}

/** 网考号：xkh 优先，没有就用 xid（字段名会骗人，两个都解析） */
export function onlineExamNumber(e: ExamTaskData): string {
  const a = e.xkh.trim();
  return a.length > 0 ? a : e.xid.trim();
}
