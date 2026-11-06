import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { Course, TimetableConfig } from './Models';
import { LocalDate } from './LocalDate';
import { Semester } from './Semester';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a04;
const FILE_NAME: string = 'widget_snapshot.json';

const DAY_CHARS: string = '一二三四五六日';

/** 卡片快照里的一门课（整学期、含 weeks 模式，卡片进程自己算「今天上什么」） */
export interface WidgetCourse {
  name: string;
  room: string | null;
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  weeks: number[];
  timeLabel: string | null;
  colorHex: string | null;
  isSelfStudy: boolean;
}

/**
 * 卡片专用快照：纯 JSON 明文存应用沙箱，**刻意不加密** ——
 * 课程名非敏感信息，而卡片是独立进程，访问 HUKS 密钥（EncryptedStore）有风险。
 * 卡片跨天/跨周自算全靠这里的 firstMonday + 每门课的 weeks。
 */
export interface WidgetSnapshotData {
  version: number;
  savedAt: number;
  termName: string;
  /** yyyy-MM-dd */
  firstMonday: string;
  totalWeeks: number;
  /** 跟随 App 主题强调色，卡片标题用它 */
  themePrimary: string;
  courses: WidgetCourse[];
}

/** 推送给卡片的渲染数据（formBindingData 只放当天摘要，体积小） */
export interface WidgetFormData {
  /** ok = 正常；empty = 今天没课；noSnapshot = 未登录/无数据；outOfTerm = 不在学期内 */
  state: string;
  dateLabel: string;
  weekLabel: string;
  countText: string;
  /** JSON: [{time, name, room, color}] */
  coursesJson: string;
  /** JSON: {time, name, room, color}；没有下一节时是空串 */
  nextJson: string;
  hint: string;
  themePrimary: string;
  /** 2*2 / 2*4，由 FormAbility 按 formId 记录后并入 */
  dim: string;
}

export function emptyFormData(hint: string, themePrimary: string): WidgetFormData {
  return {
    state: 'noSnapshot',
    dateLabel: '',
    weekLabel: '',
    countText: '',
    coursesJson: '[]',
    nextJson: '',
    hint: hint,
    themePrimary: themePrimary,
    dim: '2*4',
  };
}

/** 从领域模型构建快照（courses 传整学期课表 termCourses；拿不到时调用方传当前周兜底） */
export function buildSnapshot(config: TimetableConfig, courses: Course[],
  themePrimary: string): WidgetSnapshotData {
  return {
    version: 1,
    savedAt: Date.now(),
    termName: config.termName,
    firstMonday: config.firstMonday.toString(),
    totalWeeks: config.totalWeeks,
    themePrimary: themePrimary,
    courses: courses.map((c) => {
      const wc: WidgetCourse = {
        name: c.name,
        room: c.room,
        dayOfWeek: c.dayOfWeek,
        startSection: c.startSection,
        endSection: c.endSection,
        weeks: Array.from(c.weeks).sort((a, b) => a - b),
        timeLabel: c.timeLabel,
        colorHex: c.colorHex,
        isSelfStudy: c.isSelfStudy,
      };
      return wc;
    }),
  };
}

interface WidgetRow {
  time: string;
  name: string;
  room: string;
  color: string;
}

/** 由快照算出「今天」的卡片渲染数据。App 推送和卡片 onUpdateForm 共用这一份逻辑 */
export function summarizeForNow(snapshot: WidgetSnapshotData, now: Date): WidgetFormData {
  const today = LocalDate.of(now.getFullYear(), now.getMonth() + 1, now.getDate());
  const firstMonday = LocalDate.parse(snapshot.firstMonday);
  const out = emptyFormData('', snapshot.themePrimary);
  if (firstMonday === null) {
    out.hint = '打开 App 同步课表';
    return out;
  }

  const week = Semester.weekOf(today, firstMonday);
  out.dateLabel = `${today.monthDayLabel()} 周${DAY_CHARS.charAt(today.dayOfWeek - 1)}`;
  out.state = 'ok';
  if (week < 1) {
    out.state = 'outOfTerm';
    out.weekLabel = snapshot.termName;
    out.hint = '还没开学';
    return out;
  }
  if (week > snapshot.totalWeeks) {
    out.state = 'outOfTerm';
    out.weekLabel = snapshot.termName;
    out.hint = '本学期已结束';
    return out;
  }
  out.weekLabel = `第${week}周`;

  const todays = snapshot.courses
    .filter((c) => c.dayOfWeek === today.dayOfWeek && c.weeks.indexOf(week) >= 0)
    .sort((a, b) => a.startSection - b.startSection);
  if (todays.length === 0) {
    out.state = 'empty';
    out.countText = '今天没课';
    return out;
  }
  out.countText = `今天 ${todays.length} 节课`;

  const rows: WidgetRow[] = todays.map((c) => {
    const row: WidgetRow = {
      time: c.timeLabel ?? `${c.startSection}-${c.endSection}节`,
      name: c.name,
      room: c.isSelfStudy ? '自主学习' : (c.room ?? '—'),
      color: c.colorHex ?? snapshot.themePrimary,
    };
    return row;
  });
  out.coursesJson = JSON.stringify(rows);

  // 下一节：timeLabel 的结束时刻还没过的第一节；没有 timeLabel 就取第一节
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  let next: WidgetRow | null = null;
  for (let i = 0; i < todays.length; i++) {
    const end = endMinutesOf(todays[i].timeLabel);
    if (end === null || end > nowMinutes) {
      next = rows[i];
      break;
    }
  }
  out.nextJson = next !== null ? JSON.stringify(next) : '';
  return out;
}

/** "08:30-10:00" → 10:00 的分钟数；解析失败返回 null */
function endMinutesOf(timeLabel: string | null): number | null {
  if (timeLabel === null) {
    return null;
  }
  const m = /(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})/.exec(timeLabel);
  if (!m) {
    return null;
  }
  return Number(m[3]) * 60 + Number(m[4]);
}

/** 快照读写：明文 JSON 文件，损坏即丢弃（卡片宁可显示引导占位也不显示错乱数据） */
export class WidgetSnapshotStore {
  private readonly filePath: string;

  constructor(context: common.Context) {
    this.filePath = `${context.filesDir}/${FILE_NAME}`;
  }

  read(): WidgetSnapshotData | null {
    try {
      if (!fs.accessSync(this.filePath)) {
        return null;
      }
      const file = fs.openSync(this.filePath, fs.OpenMode.READ_ONLY);
      try {
        const stat = fs.statSync(this.filePath);
        const buffer = new ArrayBuffer(stat.size);
        fs.readSync(file.fd, buffer);
        const data = JSON.parse(util.TextDecoder.create('utf-8').decodeToString(new Uint8Array(buffer))) as WidgetSnapshotData;
        if (typeof data.firstMonday !== 'string' || !Array.isArray(data.courses)) {
          return null;
        }
        return data;
      } finally {
        fs.closeSync(file);
      }
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `卡片快照读取失败: ${JSON.stringify(e)}`);
      return null;
    }
  }

  write(data: WidgetSnapshotData): void {
    try {
      const file = fs.openSync(this.filePath,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        const bytes = util.TextEncoder.create('utf-8').encodeInto(JSON.stringify(data));
        fs.writeSync(file.fd, bytes.buffer.slice(0, bytes.byteLength));
      } finally {
        fs.closeSync(file);
      }
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `卡片快照写入失败: ${JSON.stringify(e)}`);
    }
  }

  clear(): void {
    try {
      if (fs.accessSync(this.filePath)) {
        fs.unlinkSync(this.filePath);
      }
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `卡片快照清理失败: ${JSON.stringify(e)}`);
    }
  }
}
