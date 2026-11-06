import {
  asArray,
  courseDataFrom,
  CourseData,
  examTaskDataFrom,
  ExamTaskData,
  field,
  flexInt,
  flexString,
  isObject,
  scoreDataFrom,
  ScoreData,
} from './JwModels';

/**
 * 教务 JSON 的解码入口。所有容错逻辑集中在这里（对应 Android 端 data/model/JwJson.kt）。
 */

export class JwParseException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JwParseException';
  }
}

export class TimetableResponse {
  code: number = 0;
  message: string = '';
  data: CourseData[] = [];

  get ok(): boolean {
    return this.code === 0;
  }
}

export class RowsResponse<T> {
  code: number = 0;
  message: string = '';
  rows: T[] = [];
  /** 服务器端总条数，用来判断还有没有下一页 */
  total: number = 0;

  get ok(): boolean {
    return this.code === 0;
  }

  /** rows 少于 total 说明必须翻页，否则只拿到一部分数据 */
  get hasMore(): boolean {
    return this.rows.length > 0 && this.rows.length < this.total;
  }
}

function parseRoot(body: string, what: string): Object {
  try {
    const parsed: Object = JSON.parse(body);
    if (isObject(parsed)) {
      return parsed;
    }
  } catch (e) {
    // fallthrough
  }
  throw new JwParseException(`教务${what}响应不是 JSON 对象`);
}

function responseCode(root: Object): number {
  const value = field(root, 'code');
  if (value === null) {
    return 0;
  }
  const raw = flexString(value).trim();
  const n = Number(raw);
  if (!Number.isFinite(n)) {
    throw new JwParseException('教务响应状态码无效');
  }
  return Math.trunc(n);
}

function responseMessage(root: Object): string {
  return flexString(field(root, 'message'));
}

/** rows 与 data 平级是实测形态；顺手兼容 data.rows 嵌套 */
function extractRows(root: Object, code: number, what: string): Object[] {
  const direct = asArray(field(root, 'rows'));
  if (direct !== null) {
    return direct;
  }
  const nested = asArray(field(field(root, 'data'), 'rows'));
  if (nested !== null) {
    return nested;
  }
  if (code !== 0) {
    return [];
  }
  throw new JwParseException(`教务${what}缺少 rows 数组`);
}

function extractTotal(root: Object, fallback: number): number {
  const direct = field(root, 'total');
  if (direct !== null) {
    return flexInt(direct);
  }
  const nested = field(field(root, 'data'), 'total');
  if (nested !== null) {
    return flexInt(nested);
  }
  return fallback;
}

export class JwJson {

  /**
   * 课表解码。三种形态都要能吃：
   * 1. 成功：{"code":0,"data":[...],"message":"成功"} → data 是数组
   * 2. 成功（裸数组）：[...]
   * 3. 失败：{"code":-401,"data":"","message":"尚未登录"} → data 是空字符串
   */
  static timetable(body: string): TimetableResponse {
    const text = body.trimStart();
    const out = new TimetableResponse();

    if (text.startsWith('[')) {
      const arr = JSON.parse(text) as Object[];
      out.data = arr.map((v) => courseDataFrom(v));
      return out;
    }

    const root = parseRoot(text, '课表');
    out.code = responseCode(root);
    out.message = responseMessage(root);

    const dataArray = asArray(field(root, 'data'));
    if (dataArray === null) {
      if (out.code !== 0) {
        out.data = [];
        return out;
      }
      throw new JwParseException('教务课表缺少 data 数组');
    }
    out.data = dataArray.map((v) => courseDataFrom(v));
    return out;
  }

  /**
   * 成绩解码。实测形态：{ "data": "", "rows": [...], "total": 50 }，
   * rows 与 data **平级**；兼容 data.rows 嵌套。
   */
  static scores(body: string): RowsResponse<ScoreData> {
    const root = parseRoot(body, '成绩');
    const out = new RowsResponse<ScoreData>();
    out.code = responseCode(root);
    out.message = responseMessage(root);
    const rows = extractRows(root, out.code, '成绩');
    out.rows = rows.map((v) => scoreDataFrom(v));
    out.total = extractTotal(root, out.rows.length);
    return out;
  }

  /**
   * 课程任务（考试）解码。POST /new/student/xskcrw/skrwDatas。
   * 形态与成绩接口一致，兼容裸数组。
   */
  static examTasks(body: string): RowsResponse<ExamTaskData> {
    const text = body.trimStart();
    const out = new RowsResponse<ExamTaskData>();

    if (text.startsWith('[')) {
      const arr = JSON.parse(text) as Object[];
      out.rows = arr.map((v) => examTaskDataFrom(v));
      out.total = out.rows.length;
      return out;
    }

    const root = parseRoot(text, '考试');
    out.code = responseCode(root);
    out.message = responseMessage(root);
    const rows = extractRows(root, out.code, '考试');
    out.rows = rows.map((v) => examTaskDataFrom(v));
    out.total = extractTotal(root, out.rows.length);
    return out;
  }
}
