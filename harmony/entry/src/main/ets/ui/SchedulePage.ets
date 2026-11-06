import { Course, TOTAL_SECTIONS } from '../core/Models';
import { AppViewModel } from '../viewmodel/AppViewModel';
import { contentColorFor, EmptyHint, parseHexColor, ScreenHeader, SelfStudyChip } from './Common';

/** 一节课的高度。跨节课程按 N 倍高度渲染，色块才能真正连成一块 */
const CELL_HEIGHT: number = 62;
/** 格子间的缝隙：横向由日列 padding 提供，纵向由格子自身 margin 提供 */
const CELL_GAP: number = 1;
/** 课表网格（含星期表头）的水平内边距，表头和网格必须用同一个值才能对齐 */
const GRID_INSET: number = 8;
const TIME_COL_WIDTH: number = 26;
const CHIP_WIDTH: number = 56;
const DAY_LABELS: string[] = ['一', '二', '三', '四', '五', '六', '日'];
function range(from: number, to: number): number[] {
  const result: number[] = [];
  for (let i = from; i <= to; i++) {
    result.push(i);
  }
  return result;
}

const SECTIONS: number[] = range(1, TOTAL_SECTIONS);
interface GridCell {
  /** null = 空白格 */
  course: Course | null;
  span: number;
}

/**
 * 课表页（对应 Android 端 ui/schedule/ScheduleScreen.kt）。
 *
 * 网格**永远固定渲染 14 行**，哪怕这一周 11~14 节完全没课：
 * 行数不跟 config.periods 走，所以旧缓存（只有 10 条作息）也不会把晚课截掉。
 * 跨节课程渲染成 span × CELL_HEIGHT 高的整块，而不是「一格有色 + 三格空白」。
 * 颜色用教务自己给的 bgcolor（每门课一种），比统一配色更容易一眼分辨。
 */
@Component
export struct SchedulePage {
  @ObjectLink vm: AppViewModel;
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  /** 选中周的镜像：@Watch 驱动周次条自动滚动（对应 Android 的 LaunchedEffect(selectedWeek)） */
  @Prop @Watch('onWeekChanged') selectedWeek: number = 1;
  private scroller: Scroller = new Scroller();

  build() {
    Column() {
      ScreenHeader({
        title: `第 ${this.vm.selectedWeek} 周`,
        subtitle: this.headerSubtitle(),
      })

      if (this.vm.config === null) {
        EmptyHint({ text: '还没有课表数据' })
      } else {
        this.WeekStrip()

        Blank().height(8)

        if (this.vm.weekError !== null) {
          // 加载失败不能显示成「该周 0 节」——那会让用户以为这周真的没课
          Column() {
            Text(`第 ${this.vm.selectedWeek} 周加载失败\n${this.vm.weekError ?? ''}\n\n检查网络后，重新点一下这一周`)
              .fontSize(14)
              .fontColor($r('app.color.app_text_secondary'))
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
          .padding({ left: 32, right: 32 })
        } else {
          this.DayHeader()
          this.TimetableGrid()
        }

        // 底部翻周
        Row() {
          Button('← 上一周')
            .backgroundColor(Color.Transparent)
            .fontColor(this.themePrimary)
            .onClick(() => {
              this.vm.prevWeek();
            })
          Blank()
          Button('下一周 →')
            .backgroundColor(Color.Transparent)
            .fontColor(this.themePrimary)
            .onClick(() => {
              this.vm.nextWeek();
            })
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceBetween)
        .padding({ left: 8, right: 8, top: 2, bottom: 2 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }

  private headerSubtitle(): string {
    const config = this.vm.config;
    if (config === null) {
      return '尚未连接到教务系统';
    }
    // 加载失败时别说「该周 0 节」，那是骗人的
    const tail = this.vm.weekError !== null ? '加载失败' : `该周 ${this.vm.selectedWeekTotal} 节`;
    return `${config.termName} · 共 ${config.totalWeeks} 周 · ${tail}`;
  }

  @Builder
  WeekStrip() {
    Scroll(this.scroller) {
      Row() {
        ForEach(this.weekNumbers(), (week: number) => {
          Text(this.weekLabel(week))
            .fontSize(13)
            .fontColor(week === this.vm.selectedWeek
              ? '#FFFFFF'
              : $r('app.color.app_text_primary'))
            .textAlign(TextAlign.Center)
            .width(CHIP_WIDTH)
            .padding({ top: 8, bottom: 8 })
            .backgroundColor(week === this.vm.selectedWeek
              ? this.themePrimary
              : $r('app.color.app_cell_empty'))
            .borderRadius(18)
            .margin({ left: 3, right: 3 })
            .onClick(() => {
              this.vm.selectWeek(week);
            })
        }, (week: number) => `${week}`)
      }
      .padding({ left: 16, right: 16 })
    }
    .scrollable(ScrollDirection.Horizontal)
    .scrollBar(BarState.Off)
    .width('100%')
    .onAppear(() => {
      this.scrollToSelected();
    })
  }

  private weekNumbers(): number[] {
    const total = this.vm.config?.totalWeeks ?? 0;
    const out: number[] = [];
    for (let i = 1; i <= total; i++) {
      out.push(i);
    }
    return out;
  }

  private weekLabel(week: number): string {
    return week === this.vm.currentWeek ? `${week}·今` : `${week}`;
  }

  /** 选中的周自动滚到可见位置（否则第 9 周会藏在屏幕右边） */
  private scrollToSelected(): void {
    const total = this.vm.config?.totalWeeks ?? 0;
    if (this.selectedWeek < 1 || this.selectedWeek > total) {
      return;
    }
    const offset = Math.max(0, (this.selectedWeek - 3) * (CHIP_WIDTH + 6));
    this.scroller.scrollTo({ xOffset: offset, yOffset: 0, animation: true });
  }

  private onWeekChanged(): void {
    this.scrollToSelected();
  }

  @Builder
  DayHeader() {
    // 几何必须和 TimetableGrid 完全一致：同样的水平 padding、同样的时间列宽、
    // 同样的 7 等分，星期表头才能和下面的课表列一一对齐
    Row() {
      Blank().width(TIME_COL_WIDTH)
      ForEach(DAY_LABELS, (label: string) => {
        Text(label)
          .fontSize(12)
          .fontColor($r('app.color.app_text_secondary'))
          .textAlign(TextAlign.Center)
          .layoutWeight(1)
      }, (label: string) => label)
    }
    .width('100%')
    .padding({ left: GRID_INSET, right: GRID_INSET })
  }

  @Builder
  TimetableGrid() {
    Scroll() {
      Row() {
        // 左列只显示纯数字 1..14，不显示具体时刻
        Column() {
          ForEach(SECTIONS, (index: number) => {
            Text(`${index}`)
              .fontSize(10)
              .fontColor($r('app.color.app_text_secondary'))
              .width('100%')
              .height(CELL_HEIGHT)
              .textAlign(TextAlign.Center)
              .padding({ top: 4 })
          }, (index: number) => `${index}`)
        }
        .width(TIME_COL_WIDTH)

        ForEach(this.daysGrid(), (cells: GridCell[], dayIndex: number) => {
          Column() {
            ForEach(cells, (cell: GridCell, cellIndex: number) => {
              if (cell.course !== null) {
                CourseCell({ course: cell.course, span: cell.span })
              } else {
                // 空白格。横向间隔由日列的 padding 提供：
                // 这里写 width('100%') + 横向 margin 会把格子顶出列宽（margin 在 width 外面）
                Column()
                  .width('100%')
                  .height(CELL_HEIGHT - CELL_GAP * 2)
                  .margin({ top: CELL_GAP, bottom: CELL_GAP })
                  .borderRadius(6)
                  .backgroundColor($r('app.color.app_cell_empty'))
              }
            }, (cell: GridCell, cellIndex: number) => `${dayIndex}_${cellIndex}`)
          }
          .layoutWeight(1)
          .padding({ left: CELL_GAP, right: CELL_GAP })
        }, (cells: GridCell[], dayIndex: number) => `${dayIndex}_${this.vm.selectedWeek}`)
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)
      .padding({ left: GRID_INSET, right: GRID_INSET })
    }
    .scrollable(ScrollDirection.Vertical)
    .width('100%')
    .layoutWeight(1)
  }

  /**
   * 7 天的格子数据。跨节课程占 1 个格子（span > 1），它覆盖的后续节次不再出现，
   * 所以渲染出来是连成一块的色块。
   */
  private daysGrid(): GridCell[][] {
    const week = this.vm.selectedWeek;
    const courses = this.vm.scheduleCourses.filter((c) => c.occursIn(week));
    const result: GridCell[][] = [];

    for (let day = 1; day <= 7; day++) {
      const dayCourses = courses.filter((c) => c.dayOfWeek === day);
      const starts = new Map<number, Course>();
      const covered: number[][] = [];
      for (const c of dayCourses) {
        starts.set(c.startSection, c);
        covered.push([c.startSection, c.endSection]);
      }

      const cells: GridCell[] = [];
      let section = 1;
      while (section <= TOTAL_SECTIONS) {
        const course = starts.get(section);
        if (course !== undefined) {
          const span = Math.min(Math.max(course.endSection - course.startSection + 1, 1),
            TOTAL_SECTIONS - section + 1);
          cells.push({ course: course, span: span });
          section += span;
          continue;
        }
        const isCovered = covered.some((range) => section >= range[0] && section <= range[1]);
        if (isCovered) {
          // 被跨节课程覆盖的格子：课程格已经占了它的高度
          section++;
          continue;
        }
        cells.push({ course: null, span: 1 });
        section++;
      }
      result.push(cells);
    }
    return result;
  }
}

/** 一节课程的色块 */
@Component
struct CourseCell {
  course: Course = new Course();
  span: number = 1;

  build() {
    Column() {
      Text(this.course.name)
        .fontSize(10)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.contentColor())
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      if (this.course.isSelfStudy) {
        SelfStudyChip({ compact: true })
          .margin({ top: 2 })
      }

      if (this.subText().length > 0) {
        Text(this.subText())
          .fontSize(9)
          .fontColor(this.contentColor())
          .opacity(0.85)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }

      if (this.course.content !== null) {
        Text(this.course.content ?? '')
          .fontSize(9)
          .fontColor(this.contentColor())
          .opacity(0.7)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .height(CELL_HEIGHT * this.span - CELL_GAP * 2)
    .padding(3)
    .backgroundColor(this.containerColor())
    .borderRadius(6)
    .margin({ top: CELL_GAP, bottom: CELL_GAP })
  }

  /** 教务给的配色 + ~88% 不透明度（ArkUI 支持 #AARRGGBB） */
  private containerColor(): string {
    const hex = parseHexColor(this.course.colorHex);
    if (hex !== null && hex.length === 7) {
      return `#E0${hex.substring(1)}`;
    }
    if (hex !== null) {
      return hex;
    }
    return '#E00A59F7';
  }

  private contentColor(): string {
    const hex = parseHexColor(this.course.colorHex);
    return contentColorFor(hex ?? '#0A59F7');
  }

  private subText(): string {
    return this.course.room ?? this.course.teacher ?? '';
  }
}
