import { Course, Period, TimetableConfig } from '../core/Models';
import { LocalDate } from '../core/LocalDate';
import { AppViewModel } from '../viewmodel/AppViewModel';
import { CardBox, cn, SelfStudyChip, shortTeachers } from './Common';

const DAY_LABELS: string[] = ['一', '二', '三', '四', '五', '六', '日'];

/**
 * 今日页（对应 Android 端 ui/today/TodayScreen.kt）：
 * 日期标题 · 今日课程 · 明日课程 · 本周概览柱状图 · 考试安排（含网考号）。
 */
@Component
export struct TodayPage {
  @ObjectLink vm: AppViewModel;

  build() {
    Scroll() {
      Column() {
        // 直接把日期作为页面主标题
        Text(this.dateTitle())
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.app_text_primary'))
          .width('100%')
          .padding({ left: 20, right: 20, top: 20, bottom: 8 })

        // ---- 今日课程 ----
        CardBox() {
          Text(`今日课程 ${this.vm.todayCourses.length} 节`)
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))

          if (!this.vm.todayDataReady) {
            Text(`课表还没更新到本周（当前是第 ${this.vm.currentWeek} 周）`)
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .margin({ top: 6 })
          } else if (this.vm.todayCourses.length === 0) {
            Text('今天没有课。')
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .margin({ top: 6 })
          } else {
            ForEach(this.vm.todayCourses, (course: Course, index: number) => {
              CourseRow({ course: course, config: this.vm.config })
                .margin({ top: index > 0 ? 10 : 6 })
            }, (course: Course) => course.id)
          }
        }

        // ---- 明日课程 ----
        CardBox() {
          Row() {
            Text('明日课程')
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.app_text_primary'))
              .layoutWeight(1)
            Text(this.tomorrowLabel())
              .fontSize(11)
              .fontColor($r('app.color.app_text_secondary'))
          }
          .width('100%')

          if (!this.vm.tomorrowDataReady) {
            Text(`明天是第 ${this.vm.tomorrowWeek ?? 0} 周，那周的课表还没加载`)
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .margin({ top: 6 })
          } else if (this.vm.tomorrowCourses.length === 0) {
            Text(this.tomorrowEmptyHint())
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .margin({ top: 6 })
          } else {
            ForEach(this.vm.tomorrowCourses, (course: Course, index: number) => {
              CourseRow({ course: course, config: this.vm.config })
                .margin({ top: index > 0 ? 10 : 6 })
            }, (course: Course) => course.id)
          }
        }

        // ---- 本周概览 ----
        if (this.vm.weekTotal > 0) {
          CardBox() {
            Row() {
              Text('本周概览')
                .fontSize(15)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.app_text_primary'))
                .layoutWeight(1)
              Text(`共 ${this.vm.weekTotal} 节`)
                .fontSize(12)
                .fontColor($r('app.color.app_text_secondary'))
            }
            .width('100%')

            WeekBars({ counts: this.vm.dailyCounts, todayIndex: LocalDate.now().dayOfWeek })
              .margin({ top: 12 })
          }
        }

        // ---- 考试安排 ----
        CardBox() {
          Row() {
            Text('考试安排')
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.app_text_primary'))
              .layoutWeight(1)
            if (this.vm.examNumbersReady && this.vm.upcomingExams.length > 0) {
              Text(`共 ${this.vm.upcomingExams.length} 场`)
                .fontSize(12)
                .fontColor($r('app.color.app_text_secondary'))
            }
          }
          .width('100%')

          if (!this.vm.examNumbersReady) {
            // 网考号还没回来：整张考试列表先别画，
            // 否则列表先渲染、号码过一秒再「跳」出来，看着像闪烁
            Row() {
              LoadingProgress()
                .width(14)
                .height(14)
              Text('正在加载考试安排…')
                .fontSize(11)
                .fontColor($r('app.color.app_text_secondary'))
                .margin({ left: 8 })
            }
            .margin({ top: 6 })
          } else if (this.vm.upcomingExams.length === 0) {
            Text('近期暂无考试安排')
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .margin({ top: 6 })
          } else {
            // 全部显示，不截断
            ForEach(this.vm.upcomingExams, (exam: Course, index: number) => {
              ExamRow({ exam: exam, examNumbers: this.vm.examNumbers })
                .margin({ top: index > 0 ? 10 : 6 })
            }, (exam: Course) => exam.id)
          }
        }

        if (this.vm.loading) {
          CardBox() {
            Text('正在加载…')
              .fontSize(13)
              .fontColor($r('app.color.app_text_secondary'))
          }
        }

        if (this.vm.error !== null) {
          CardBox() {
            Text(this.vm.error ?? '')
              .fontSize(13)
              .fontColor($r('app.color.app_error'))
          }
        }
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding({ bottom: 16 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }

  private dateTitle(): string {
    const today = LocalDate.now();
    return `${today.toString()} · 星期${cn(today.dayOfWeek)} · 第 ${this.vm.currentWeek} 周`;
  }

  private tomorrowLabel(): string {
    const d = LocalDate.now().plusDays(1);
    return `明天 · ${d.monthDayLabel()} 星期${cn(d.dayOfWeek)}`;
  }

  private tomorrowEmptyHint(): string {
    const day = LocalDate.now().plusDays(1).dayOfWeek;
    if (day === 6) {
      return '明天是周六，没有课，睡个懒觉吧';
    }
    if (day === 7) {
      return '明天是周日，没有课，好好休息';
    }
    return '明天没有课，好好休息';
  }
}

/** 一条课程：左侧时间轴 + 右侧课名/教室/内容 */
@Component
struct CourseRow {
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  course: Course = new Course();
  config: TimetableConfig | null = null;

  build() {
    Row() {
      Column() {
        Text(this.startText())
          .fontSize(12)
          .fontColor(this.themePrimary)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.endText())
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
      }
      .alignItems(HorizontalAlign.Start)
      .width(64)

      Column() {
        Row() {
          Text(this.course.name)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
            .layoutWeight(1)
          if (this.course.isSelfStudy) {
            SelfStudyChip()
              .margin({ left: 6 })
          }
        }
        .width('100%')

        if (this.subtitle().length > 0) {
          Text(this.subtitle())
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Top)
  }

  /** 时间轴：教务给的 qssj/jssj 优先，退回本地作息表，再退到「第 N 节」 */
  private startText(): string {
    const label = this.course.timeLabel;
    if (label !== null) {
      const start = label.split('-')[0];
      if (start.length > 0) {
        return start;
      }
    }
    const period = this.periodAt(this.course.startSection);
    return period !== null ? period.start : `第${this.course.startSection}节`;
  }

  private endText(): string {
    const label = this.course.timeLabel;
    if (label !== null) {
      const parts = label.split('-');
      if (parts.length > 1 && parts[1].length > 0) {
        return parts[1];
      }
    }
    const period = this.periodAt(this.course.endSection);
    return period !== null ? period.end : `第${this.course.endSection}节`;
  }

  private periodAt(section: number): Period | null {
    const periods = this.config?.periods;
    if (periods === undefined || periods === null) {
      return null;
    }
    for (const p of periods) {
      if (p.index === section) {
        return p;
      }
    }
    return null;
  }

  private subtitle(): string {
    const parts: string[] = [];
    // 自主学习不用去教室，别显示一个空教室占位
    if (this.course.isSelfStudy) {
      parts.push('无需前往教室');
    } else if (this.course.room !== null) {
      parts.push(this.course.room);
    }
    const teachers = shortTeachers(this.course.teacher);
    if (teachers !== null) {
      parts.push(teachers);
    }
    if (this.course.content !== null) {
      parts.push(this.course.content);
    }
    return parts.join(' · ');
  }
}

/** 一条考试：日期 + 课名（带网考号）+ 地点 · 时间 */
@Component
struct ExamRow {
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  exam: Course = new Course();
  examNumbers: Map<string, string> = new Map<string, string>();

  build() {
    Row() {
      Column() {
        Text(this.exam.date !== null ? this.exam.date.monthDayLabel() : '')
          .fontSize(12)
          .fontColor(this.themePrimary)
        Text(this.exam.date !== null ? `周${cn(this.exam.date.dayOfWeek)}` : '')
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
      }
      .alignItems(HorizontalAlign.Start)
      .width(72)

      Column() {
        Text() {
          Span(this.exam.name)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.app_text_primary'))
          if (this.examNumber() !== null) {
            Span(` [网考号：${this.examNumber()}]`)
              .fontSize(11)
              .fontWeight(FontWeight.Normal)
              .fontColor($r('app.color.app_text_secondary'))
          }
        }

        Text(this.where())
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }

  /** 课程名 → 网考号；没有就只显示课名（课名两边偶尔带空格，精确匹配不上再 trim 一次） */
  private examNumber(): string | null {
    const exact = this.examNumbers.get(this.exam.name);
    const trimmed = this.examNumbers.get(this.exam.name.trim());
    const number = exact ?? trimmed;
    if (number === undefined || number.trim().length === 0) {
      return null;
    }
    return number;
  }

  private where(): string {
    const room = this.exam.room !== null && this.exam.room.trim().length > 0
      ? this.exam.room
      : '地点待定';
    return this.exam.timeLabel !== null ? `${room} · ${this.exam.timeLabel}` : room;
  }
}

/** 每天几节课的柱状概览，今天高亮 */
@Component
struct WeekBars {
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  @StorageProp('themeSoft') readonly themeSoft: string = '#400A59F7';
  counts: number[] = [];
  todayIndex: number = 1;

  build() {
    Row() {
      ForEach(this.counts, (count: number, index: number) => {
        Column() {
          Text(`${count}`)
            .fontSize(11)
            .fontColor(this.isToday(index)
              ? this.themePrimary
              : $r('app.color.app_text_secondary'))

          Column()
            .width('100%')
            .height(this.barHeight(count))
            .borderRadius({ topLeft: 4, topRight: 4 })
            .backgroundColor(this.isToday(index)
              ? this.themePrimary
              : this.themeSoft)
            .margin({ top: 4 })

          Text(DAY_LABELS[index])
            .fontSize(11)
            .fontColor(this.isToday(index)
              ? this.themePrimary
              : $r('app.color.app_text_secondary'))
            .margin({ top: 6 })
        }
        .layoutWeight(1)
        .height('100%')
        .justifyContent(FlexAlign.End)
      }, (count: number, index: number) => `${index}`)
    }
    .width('100%')
    .height(104)
  }

  private isToday(index: number): boolean {
    return index + 1 === this.todayIndex;
  }

  private barHeight(count: number): number {
    let max = 1;
    for (const c of this.counts) {
      if (c > max) {
        max = c;
      }
    }
    const ratio = count / max;
    const h = 56 * ratio;
    return Math.max(h, count === 0 ? 3 : 8);
  }
}
