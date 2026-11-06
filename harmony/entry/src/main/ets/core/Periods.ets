import { Period } from './Models';

/**
 * 作息时间表（第 1~14 节的起止时刻）。
 *
 * 教务**没有**「每节课几点上」的接口 —— qssj/jssj 是**每门课**的起止时刻，
 * 不是每节课的。所以这张表只能本地维护，RealJw 与 FakeJw 共用这一份。
 *
 * 1~12 节用真实抓包（timetable_term.json / timetable_week11.json）反推校准：
 * 每节课 40 分钟，相邻两节之间休息 5 分钟，大节之间休息 20 分钟。
 * 第 13、14 节按同样的节奏往后顺延。学校若改了作息，改这一处即可。
 */
export class DefaultPeriods {
  static readonly ALL: Period[] = [
    new Period(1, '08:00', '08:40'),
    new Period(2, '08:45', '09:25'),
    new Period(3, '09:50', '10:30'),
    new Period(4, '10:35', '11:15'),
    new Period(5, '11:20', '12:00'),
    new Period(6, '14:30', '15:10'),
    new Period(7, '15:15', '15:55'),
    new Period(8, '16:15', '16:55'),
    new Period(9, '17:00', '17:40'),
    new Period(10, '19:30', '20:10'),
    new Period(11, '20:15', '20:55'),
    new Period(12, '21:00', '21:40'),
    new Period(13, '21:45', '22:25'),
    new Period(14, '22:30', '23:10'),
  ];
}
