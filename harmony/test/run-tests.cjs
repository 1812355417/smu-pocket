/**
 * 鸿蒙移植逻辑层的 Node 验证：直接跑编译后的 JS，断言镜像 Android 端单测。
 * fixture 复用 app/src/test/resources/fixtures（同一份脱敏真实抓包）。
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');

const REPO = process.env.REPO_ROOT || path.join(__dirname, '../..');
const OUT = process.argv[2];
const FIXTURES = path.join(REPO, 'app/src/test/resources/fixtures');
const fixture = (name) => fs.readFileSync(path.join(FIXTURES, name), 'utf8');

const { LocalDate } = require(OUT + '/core/LocalDate');
const { Semester } = require(OUT + '/core/Semester');
const { TermCode } = require(OUT + '/core/TermCode');
const TimeUtils = require(OUT + '/core/TimeUtils');
const { JwJson } = require(OUT + '/data/JwJson');
const { toCourse, toGrade } = require(OUT + '/data/Mappers');
const { scoreOf, gradePointOf, creditOf, onlineExamNumber } = require(OUT + '/data/JwModels');
const { collectPages, Page } = require(OUT + '/data/Pagination');
const { splitSetCookie, parseSetCookie, buildCookieHeader } = require(OUT + '/core/Session');
const { resolveHttpUrl } = require(OUT + '/core/Transport');

let passed = 0;
let failed = 0;
function test(name, fn) {
  try {
    fn();
    passed++;
    console.log(`  ✓ ${name}`);
  } catch (e) {
    failed++;
    console.error(`  ✗ ${name}\n    ${e.message}`);
  }
}
const eq = (a, b) => assert.strictEqual(a, b);

const firstMonday = LocalDate.of(2026, 8, 31);

// ---------------------------------------------------------------------
console.log('LocalDate');
test('parse 兼容带时间后缀', () => {
  eq(LocalDate.parse('2026-10-23').toString(), '2026-10-23');
  eq(LocalDate.parse('2026-10-23 00:00:00').toString(), '2026-10-23');
  eq(LocalDate.parse(null), null);
  eq(LocalDate.parse(''), null);
  eq(LocalDate.parse('2026/10/23'), null);
});
test('dayOfWeek：2026-10-23 是周五', () => {
  eq(LocalDate.parse('2026-10-23').dayOfWeek, 5);
  eq(LocalDate.of(2026, 8, 31).dayOfWeek, 1); // 第一周周一必须是周一
  eq(LocalDate.of(1970, 1, 1).dayOfWeek, 4);  // 1970-01-01 周四
});
test('plusWeeks / plusDays / isBefore', () => {
  eq(firstMonday.plusWeeks(8).toString(), '2026-10-26');
  eq(firstMonday.plusDays(4).toString(), '2026-09-04');
  assert.ok(LocalDate.of(2026, 1, 1).isBefore(LocalDate.of(2026, 1, 2)));
});
test('dateOf 由 第一周周一+周次+星期 还原日期', () => {
  eq(TimeUtils.dateOf(firstMonday, 8, 5).toString(), '2026-10-23');
});

// ---------------------------------------------------------------------
console.log('Semester / 周次');
test('weekOf', () => {
  eq(Semester.weekOf(firstMonday, firstMonday), 1);
  eq(Semester.weekOf(LocalDate.of(2026, 10, 19), firstMonday), 8);
  eq(Semester.weekOf(LocalDate.of(2026, 10, 26), firstMonday), 9);
  eq(Semester.weekOf(LocalDate.of(2026, 8, 24), firstMonday), 0); // 开学前是 0 不是 1
});
test('parseWeeks 容错各种格式', () => {
  assert.deepStrictEqual([...TimeUtils.parseWeekList('8')], [8]);
  assert.deepStrictEqual([...TimeUtils.parseWeekList('12,13')], [12, 13]);
  assert.deepStrictEqual([...TimeUtils.parseWeekList('13,10,15,9,4')].sort((a, b) => a - b), [4, 9, 10, 13, 15]);
  assert.deepStrictEqual([...TimeUtils.parseWeekList('1-4')], [1, 2, 3, 4]);
  assert.deepStrictEqual([...TimeUtils.parseWeekList('1-4单')], [1, 3]);
  assert.deepStrictEqual([...TimeUtils.parseWeekList('1-4双')], [2, 4]);
  eq(TimeUtils.parseWeekList(null).size, 0);
});
test('clampWeek 钳住', () => {
  eq(Semester.clampWeek(0, 20), 1);
  eq(Semester.clampWeek(25, 20), 20);
  eq(Semester.clampWeek(9, 0), 1);
});

// ---------------------------------------------------------------------
console.log('TimeUtils');
test('inferFirstMonday 两条独立记录投票', () => {
  const rows = [
    { kcmc: '形势与政策', zc: '8', xq: 5, qsrq: '2026-10-23', jcdm: '0809', ps: '08' },
    { kcmc: '临床诊断学', zc: '6', xq: 4, qsrq: '2026-10-08', jcdm: '060708', ps: '06' },
  ];
  eq(TimeUtils.inferFirstMonday(rows).toString(), '2026-08-31');
  eq(TimeUtils.inferFirstMonday([rows[0]]).toString(), '2026-08-31');
  eq(TimeUtils.inferFirstMonday([rows[1]]).toString(), '2026-08-31');
});
test('inferFirstMonday 没有 qsrq 必须返回 null（不瞎猜）', () => {
  eq(TimeUtils.inferFirstMonday([{ kcmc: 'x', zc: '12,13', xq: 5, ps: '03', pe: '04' }]), null);
});
test('inferFirstMonday 坏 xq 行被忽略', () => {
  eq(TimeUtils.inferFirstMonday([{ kcmc: 'x', zc: '8', xq: 0, qsrq: '2026-10-23' }]), null);
});
test('inferFirstMonday 没有 zc 时用 queriedWeek 兜底', () => {
  const rows = [
    { kcmc: 'a', zc: '', xq: 2, qsrq: '2026-10-27' },
    { kcmc: 'b', zc: '', xq: 4, qsrq: '2026-10-29' },
  ];
  eq(TimeUtils.inferFirstMonday(rows, 9).toString(), '2026-08-31');
  eq(TimeUtils.inferFirstMonday(rows), null);
});
test('weekBounds 与抓包 payload 一致', () => {
  const b = new TimeUtils.WeekBounds(9, firstMonday);
  eq(b.d1, '2026-10-26 00:00:00');
  eq(b.d2NextMonday, '2026-11-02 00:00:00');
  eq(b.d2Sunday, '2026-11-01 00:00:00');
  eq(b.label, '10/26 ~ 11/1');
});
test('parseJcdm / sectionsOf', () => {
  assert.deepStrictEqual(TimeUtils.parseJcdm('0809'), [8, 9]);
  assert.deepStrictEqual(TimeUtils.parseJcdm('060708'), [6, 8]);
  eq(TimeUtils.parseJcdm('080'), null);
  eq(TimeUtils.parseJcdm(''), null);
  eq(TimeUtils.parseJcdm(null), null);
  assert.deepStrictEqual(TimeUtils.sectionsOf({ jcdm: '0809', ps: '06', pe: '07' }), [8, 9]);
  assert.deepStrictEqual(TimeUtils.sectionsOf({ jcdm: null, ps: '03', pe: '04' }), [3, 4]);
  assert.deepStrictEqual(TimeUtils.sectionsOf({ jcdm: null, ps: '08', pe: null }), [8, 8]);
  eq(TimeUtils.sectionsOf({ jcdm: null, ps: null, pe: null }), null);
});
test('clockRange', () => {
  eq(TimeUtils.clockRange({ qssj: '09:50:00', jssj: '11:15:00' }), '09:50-11:15');
  eq(TimeUtils.clockRange({ qssj: null, jssj: null }), null);
});

// ---------------------------------------------------------------------
console.log('TermCode');
test('fromDisplayName / displayName', () => {
  eq(TermCode.fromDisplayName('2026-2027-1'), '202601');
  eq(TermCode.fromDisplayName('2026-2027 学年 第 1 学期'), '202601');
  eq(TermCode.fromDisplayName('2025-2026-2'), '202502');
  eq(TermCode.fromDisplayName('乱写的'), null);
  eq(TermCode.displayName('202601'), '2026-2027 学年 第 1 学期');
});
test('fromDate 按 9 月开学推算', () => {
  eq(TermCode.fromDate(LocalDate.of(2026, 11, 6)), '202601'); // 11 月 → 当年第 1 学期
  eq(TermCode.fromDate(LocalDate.of(2026, 9, 1)), '202601');
  eq(TermCode.fromDate(LocalDate.of(2027, 1, 15)), '202601'); // 1 月 → 上一年第 1 学期
  eq(TermCode.fromDate(LocalDate.of(2027, 3, 1)), '202602');  // 2~8 月 → 上一年第 2 学期
  eq(TermCode.fromDate(LocalDate.of(2026, 8, 31)), '202502');
});
test('isPlausible 挡住 203402 这种假学期', () => {
  const today = LocalDate.of(2026, 11, 6);
  assert.ok(TermCode.isPlausible('202601', today));
  assert.ok(!TermCode.isPlausible('203402', today));
  assert.ok(!TermCode.isPlausible('202603', today));
  assert.ok(!TermCode.isPlausible('abc', today));
});

// ---------------------------------------------------------------------
console.log('JwJson：课表 fixture');
test('整学期总表：30 行全部解析+映射', () => {
  const resp = JwJson.timetable(fixture('timetable_term.json'));
  eq(resp.code, 0);
  assert.ok(resp.ok);
  eq(resp.data.length, 30);
  assert.ok(resp.data.every((r) => r.xq >= 1 && r.xq <= 7));
  assert.ok(resp.data.every((r) => r.zc.length > 0));
  assert.ok(resp.data.every((r) => r.qsrq === null)); // 总表没有 qsrq
  eq(TimeUtils.inferFirstMonday(resp.data), null);

  const multi = resp.data.filter((r) => r.zc.indexOf(',') >= 0);
  assert.ok(multi.length > 0, '总表应有多周串');
  assert.ok(TimeUtils.parseWeekList(multi[0].zc).size > 1);

  const courses = resp.data.map((r) => toCourse(r, null, firstMonday)).filter((c) => c !== null);
  eq(courses.length, 30);
  assert.ok(courses.every((c) => c.weeks.size > 0));
  assert.ok(courses.every((c) => c.room !== ''));
});
test('按周查询：2 条真实记录，反推第一周周一', () => {
  const resp = JwJson.timetable(fixture('timetable_week.json'));
  eq(resp.data.length, 2);
  eq(resp.data[0].sknrjj, '形势与政策专题二');
  eq(resp.data[0].qsrq, '2026-10-23');
  eq(resp.data[0].zc, '8'); // 数字 8 被柔性转成字符串
  eq(resp.data[0].xq, 5);
  assert.deepStrictEqual(TimeUtils.sectionsOf(resp.data[0]), [8, 9]);
  eq(TimeUtils.inferFirstMonday(resp.data).toString(), '2026-08-31');
});
test('缺失 zc 的按日期响应：不抛异常，靠 fallbackWeek 映射', () => {
  const resp = JwJson.timetable(fixture('timetable_week_no_zc.json'));
  eq(resp.data.length, 2);
  eq(resp.data[0].zc, '');
  const courses = resp.data.map((r) => toCourse(r, 9, null)).filter((c) => c !== null);
  eq(courses.length, 2);
  assert.ok(courses.every((c) => c.weeks.size === 1 && c.weeks.has(9)));
  eq(courses[0].date.toString(), '2026-10-27');
  eq(courses[1].room, '304机房');
  // 不传 fallbackWeek 时整门课被丢掉（刻意的）
  eq(resp.data.map((r) => toCourse(r)).filter((c) => c !== null).length, 0);
});
test('真实第 11 周：7 门课全部解析（回归）', () => {
  const resp = JwJson.timetable(fixture('timetable_week11.json'));
  eq(resp.code, 0);
  eq(resp.data.length, 7);
  assert.ok(resp.data.every((r) => r.zc === '11'));
  const courses = resp.data.map((r) => toCourse(r, 11, null)).filter((c) => c !== null);
  eq(courses.length, 7);
  assert.ok(courses.every((c) => c.occursIn(11)));
  assert.deepStrictEqual(courses.map((c) => c.name), [
    '机能实验学二', '药理学', '药理学', '实验诊断学', '实验诊断学', '手术学基础', '临床诊断学',
  ]);
});

// ---------------------------------------------------------------------
console.log('JwJson：成绩 fixture');
test('成绩平级 rows：20 行 / total=50 / hasMore', () => {
  const resp = JwJson.scores(fixture('scores_page1.json'));
  eq(resp.rows.length, 20);
  eq(resp.total, 50);
  assert.ok(resp.hasMore, '20 < 50 必须翻页');
});
test('成绩字段容错：数字/字符串混给', () => {
  const first = JwJson.scores(fixture('scores_page1.json')).rows[0];
  eq(first.xnxqmc, '2025-2026-2');
  eq(first.xnxqdm, '202502');
  assert.ok(Math.abs(scoreOf(first) - 88.2) < 0.001);
  eq(first.zcj, '88.20');
  assert.ok(Math.abs(creditOf(first) - 3.0) < 0.001);
  assert.ok(Math.abs(gradePointOf(first) - 3.8) < 0.001);
  eq(first.kcmc, '习近平新时代中国特色社会主义思想概论');
  const grade = toGrade(first);
  eq(grade.course, '习近平新时代中国特色社会主义思想概论');
  eq(grade.term, '2025-2026-2');
});

// ---------------------------------------------------------------------
console.log('JwJson：考试网考号');
test('skrwDatas 形状 + xid 数字容错', () => {
  const body = JSON.stringify({
    code: 0, message: '成功', data: '', total: 2,
    rows: [
      { xid: '7667619', kcmc: '形势与政策', khfsmc: '考试' },
      { xid: 13621854, kcmc: '药理学', khfsmc: '考试' },
    ],
  });
  const resp = JwJson.examTasks(body);
  eq(resp.code, 0);
  eq(resp.rows.length, 2);
  eq(onlineExamNumber(resp.rows[0]), '7667619');
  eq(onlineExamNumber(resp.rows[1]), '13621854'); // 数字也能吃
  assert.ok(!resp.hasMore);
});
test('xkh 优先、回退 xid', () => {
  eq(onlineExamNumber(JwJson.examTasks('{"code":0,"rows":[{"xkh":"111","xid":"222","kcmc":"A"}]}').rows[0]), '111');
  eq(onlineExamNumber(JwJson.examTasks('{"code":0,"rows":[{"xid":"222","kcmc":"A"}]}').rows[0]), '222');
  eq(onlineExamNumber(JwJson.examTasks('{"code":0,"rows":[{"kcmc":"A"}]}').rows[0]), '');
  const nested = JwJson.examTasks('{"code":0,"data":{"rows":[{"xkh":"1","kcmc":"A"}]}}');
  eq(nested.rows.length, 1);
  eq(nested.rows[0].kcmc, 'A');
});

// ---------------------------------------------------------------------
console.log('JwJson：未登录信封不炸');
test('code=-401 三种接口都容错', () => {
  const body = '{"code":-401,"data":"","message":"尚未登录，请先登录"}';
  const t = JwJson.timetable(body);
  eq(t.code, -401);
  eq(t.message, '尚未登录，请先登录');
  eq(t.data.length, 0);
  assert.ok(!t.ok);
  const s = JwJson.scores(body);
  eq(s.code, -401);
  eq(s.rows.length, 0);
  assert.ok(!s.hasMore);
  const e = JwJson.examTasks(body);
  eq(e.code, -401);
  eq(e.rows.length, 0);
});
test('裸数组课表也能吃', () => {
  const resp = JwJson.timetable('[{"kcmc":"x","xq":1,"ps":"01","pe":"02","zc":"1"}]');
  eq(resp.data.length, 1);
});

// ---------------------------------------------------------------------
console.log('Session（cookie 语义，对齐 OkHttp Cookie）');
const NOW = Date.parse('2026-11-07T12:00:00Z');

test('splitSetCookie 拆开合并串，Expires 里的逗号不拆', () => {
  assert.deepStrictEqual(
    splitSetCookie('JSESSIONID=abc; Path=/; HttpOnly, OTHER=1; Path=/'),
    ['JSESSIONID=abc; Path=/; HttpOnly', 'OTHER=1; Path=/']);
  const withExpires = splitSetCookie('A=1; Expires=Wed, 21 Oct 2099 07:28:00 GMT; Path=/, B=2');
  eq(withExpires.length, 2);
  assert.ok(withExpires[0].includes('Wed, 21 Oct 2099'), 'Expires 必须完整留在第一条里');
  assert.deepStrictEqual(splitSetCookie('A=1\nB=2'), ['A=1', 'B=2']);
});

test('parseSetCookie：host-only、默认 Path、HttpOnly', () => {
  const p = parseSetCookie('JSESSIONID=abc; Path=/jw; HttpOnly',
    'zhjw.smu.edu.cn', '/new/student/x', NOW);
  eq(p.domain, 'zhjw.smu.edu.cn');
  eq(p.name, 'JSESSIONID');
  eq(p.entry.value, 'abc');
  eq(p.entry.hostOnly, true);
  eq(p.entry.path, '/jw');
  eq(p.entry.expiresAt, null);
  eq(p.expired, false);
  // 没带 Path → 取请求路径的目录
  eq(parseSetCookie('A=1', 'h', '/a/b', NOW).entry.path, '/a');
  eq(parseSetCookie('A=1', 'h', '/a', NOW).entry.path, '/');
});

test('parseSetCookie：Domain 父域共享 + 拒绝跨域盖章', () => {
  const parent = parseSetCookie('CAS=id; Domain=.smu.edu.cn; Path=/',
    'cas.smu.edu.cn', '/', NOW);
  eq(parent.domain, 'smu.edu.cn');
  eq(parent.entry.hostOnly, false);
  // Domain 不是请求 host 的后缀 → 忽略该属性，退回 host-only（OkHttp 同）
  const rejected = parseSetCookie('X=1; Domain=.other.com', 'zhjw.smu.edu.cn', '/', NOW);
  eq(rejected.domain, 'zhjw.smu.edu.cn');
  eq(rejected.entry.hostOnly, true);
});

test('parseSetCookie：Max-Age=0 是删除，Max-Age 优先于 Expires', () => {
  eq(parseSetCookie('JSESSIONID=; Max-Age=0', 'h', '/', NOW).expired, true);
  eq(parseSetCookie('A=1; Expires=Wed, 21 Oct 2015 07:28:00 GMT', 'h', '/', NOW).expired, true);
  const alive = parseSetCookie('A=1; Max-Age=3600', 'h', '/', NOW);
  eq(alive.expired, false);
  eq(alive.entry.expiresAt, NOW + 3600 * 1000);
  // Max-Age 覆盖 Expires（即使 Expires 已过期）
  eq(parseSetCookie('A=1; Expires=Wed, 21 Oct 2015 07:28:00 GMT; Max-Age=3600',
    'h', '/', NOW).expired, false);
});

test('buildCookieHeader：域 / 路径 / Secure / 过期四项过滤', () => {
  const tables = new Map();
  const zhjw = new Map();
  zhjw.set('JSESSIONID', { value: 'abc', hostOnly: true, path: '/', secure: false, expiresAt: null });
  zhjw.set('OLD', { value: 'x', hostOnly: true, path: '/', secure: false, expiresAt: NOW - 1000 });
  zhjw.set('SEC', { value: 's', hostOnly: true, path: '/', secure: true, expiresAt: null });
  zhjw.set('DEEP', { value: 'd', hostOnly: true, path: '/new', secure: false, expiresAt: null });
  tables.set('zhjw.smu.edu.cn', zhjw);
  const parent = new Map();
  parent.set('CAS', { value: 'id', hostOnly: false, path: '/', secure: false, expiresAt: null });
  tables.set('smu.edu.cn', parent);

  const header = buildCookieHeader(tables, 'https://zhjw.smu.edu.cn/new/student/x', NOW);
  assert.ok(header.includes('JSESSIONID=abc'), '同 host 要发');
  assert.ok(header.includes('CAS=id'), '父域 cookie 子域共享');
  assert.ok(header.includes('DEEP=d'), '路径前缀命中要发');
  assert.ok(!header.includes('OLD='), '过期的不发');
  assert.ok(header.includes('SEC=s'), 'https 下 Secure 要发');
});
test('buildCookieHeader：Secure 只走 https，host-only 不共享', () => {
  const tables = new Map();
  const zhjw = new Map();
  zhjw.set('SEC', { value: 's', hostOnly: true, path: '/', secure: true, expiresAt: null });
  tables.set('zhjw.smu.edu.cn', zhjw);
  eq(buildCookieHeader(tables, 'http://zhjw.smu.edu.cn/x', NOW), '');
  eq(buildCookieHeader(tables, 'https://zhjw.smu.edu.cn/x', NOW), 'SEC=s');
  eq(buildCookieHeader(tables, 'https://other.smu.edu.cn/x', NOW), '');
  eq(buildCookieHeader(tables, 'https://zhjw.smu.edu.cn/other/x', NOW), 'SEC=s');
});

// ---------------------------------------------------------------------
console.log('Transport（重定向 Location 解析）');
test('resolveHttpUrl：绝对 / 协议相对 / 根相对 / 目录相对', () => {
  eq(resolveHttpUrl('https://a.b/c', 'https://x.y/z'), 'https://a.b/c');
  eq(resolveHttpUrl('//cdn.x.com/a', 'https://x.y/z'), 'https://cdn.x.com/a');
  eq(resolveHttpUrl('/login', 'https://zhjw.smu.edu.cn/new/x'), 'https://zhjw.smu.edu.cn/login');
  eq(resolveHttpUrl('index.jsp', 'https://h/new/student'), 'https://h/new/index.jsp');
  eq(resolveHttpUrl('?a=1', 'https://h/p/x'), 'https://h/p/x?a=1');
  eq(resolveHttpUrl('', 'https://h/'), null);
});

// ---------------------------------------------------------------------
console.log('Pagination（异步）');
async function paginationTests() {
  // 正常翻页：total=50，每页 20
  {
    const pages = [20, 20, 10];
    const rows = await collectPages(async (p) => new Page(
      Array.from({ length: pages[p - 1] }, (_, i) => (p - 1) * 20 + i), 50));
    eq(rows.length, 50);
    eq(rows[49], 49);
  }
  // 重复页 → 拒绝
  await assert.rejects(
    collectPages(async () => new Page([1, 2], 4)),
    /重复分页/);
  // 提前空页 → 拒绝
  await assert.rejects(
    collectPages(async (p) => new Page(p === 1 ? [1] : [], 5)),
    /提前结束/);
  // 总数回退 → 拒绝
  await assert.rejects(
    collectPages(async (p) => new Page([p], p === 1 ? 3 : 1)),
    /总数不一致/);
  console.log('  ✓ collectPages 正常/重复页/提前空页/总数回退');
}

paginationTests().then(() => {
  console.log(`\n结果：${passed + 1} 项通过，${failed} 项失败`);
  process.exit(failed > 0 ? 1 : 0);
}).catch((e) => {
  console.error('分页测试异常:', e);
  process.exit(1);
});

// ---------------------------------------------------------------------
// 追加：状态中枢 AppViewModel（FakeJw 演示数据全链路）
// ---------------------------------------------------------------------
async function viewModelTests() {
  console.log('AppViewModel（演示数据全链路）');
  const { AppViewModel } = require(OUT + '/viewmodel/AppViewModel');
  const { FakeJw } = require(OUT + '/data/JwService');

  // 用最小桩代替 SessionStore / CacheStore（逻辑层测试不碰平台能力）
  const session = {
    isLoggedIn: () => true,
    clear: async () => {},
    saveRawCookieHeader: async () => {},
  };
  const cache = {
    load: async () => null,
    save: async () => {},
    clear: () => {},
  };

  const vm = new AppViewModel(() => new FakeJw(), session, cache);
  vm.enterDemo();
  // 等 loadAll 跑完（含网考号与成绩）
  for (let i = 0; i < 100 && (vm.loading || !vm.examNumbersReady || !vm.gradesLoaded); i++) {
    await new Promise((r) => setTimeout(r, 50));
  }

  try {
    assert.ok(vm.loggedIn && vm.demoMode, '演示模式应已登录');
    assert.ok(vm.config !== null, 'config 应就绪');
    assert.strictEqual(vm.currentWeek, 6, '演示数据把第一周定在 5 周前 → 当前第 6 周');
    assert.strictEqual(vm.selectedWeek, 6, '默认选中当前周');
    assert.strictEqual(vm.grades.length, 6, '演示成绩 6 门');
    assert.strictEqual(vm.termCourses.length, 14, '整学期 = 12 门课 + 2 场考试');
    assert.strictEqual(vm.examNumbers.size, 2, '演示网考号 2 个');
    assert.strictEqual(vm.upcomingExams.length, 2, '两场考试都在未来');
    assert.ok(vm.weekTotal > 0, '本周应有课');
    assert.ok(vm.weightedGpa !== null && vm.weightedGpa > 3, '加权绩点应算得出来');
    assert.ok(vm.gradesByTerm.length === 2, '成绩应分成 2 个学期');
    assert.ok(vm.gradesByTerm[0].term > vm.gradesByTerm[1].term, '学期分组新的在前');
    console.log('  ✓ enterDemo 全链路：config/周次/成绩/考试/网考号/统计');
    passed++;
  } catch (e) {
    failed++;
    console.error(`  ✗ AppViewModel 全链路\n    ${e.message}`);
  }
  console.log(`\n最终结果：${passed} 项通过，${failed} 项失败`);
  process.exit(failed > 0 ? 1 : 0);
}

if (process.env.SKIP_VM !== '1') {
  // 等待文件末尾的分页测试完成后再跑（它先打印一次小结并退出——拦截它的 exit）
  const origExit = process.exit;
  process.exit = (code) => {
    process.exit = origExit;
    viewModelTests();
  };
}
