import { Grade } from '../core/Models';
import { AppViewModel, TermGrades } from '../viewmodel/AppViewModel';
import { CardBox, EmptyHint, fmt, ScreenHeader } from './Common';

/**
 * 成绩页（对应 Android 端 ui/grades/GradesScreen.kt）。
 * 结构：List + 按学期折叠。默认**全部收起**，用户点了哪个就展开哪个。
 */
@Component
export struct GradesPage {
  @ObjectLink vm: AppViewModel;
  @State expandedTerms: string[] = [];

  build() {
    Column() {
      if (this.vm.gradesByTerm.length === 0) {
        ScreenHeader({ title: '成绩', subtitle: '还没有成绩数据' })
        EmptyHint({ text: '还没有成绩数据' })
      } else {
        List() {
          ListItem() {
            ScreenHeader({
              title: '成绩',
              subtitle: `共 ${this.vm.grades.length} 门 · ${this.vm.gradesByTerm.length} 个学期 · 总学分 ${fmt(this.vm.totalCredit, 1)}`,
            })
          }

          ListItem() {
            this.SummaryCard()
          }

          ForEach(this.vm.gradesByTerm, (group: TermGrades) => {
            ListItemGroup({ header: this.TermHeader(group) }) {
              if (this.isExpanded(group.term)) {
                ForEach(group.list, (grade: Grade, index: number) => {
                  ListItem() {
                    GradeRow({ grade: grade })
                  }
                }, (grade: Grade, index: number) => `${group.term}_${index}_${grade.course}`)
              }
            }
            .padding({ left: 16, right: 16 })
          }, (group: TermGrades) => group.term)

          ListItem() {
            Row().height(16)
          }
        }
        .width('100%')
        .layoutWeight(1)
        .sticky(StickyStyle.None)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.app_page_bg'))
  }

  private isExpanded(term: string): boolean {
    return this.expandedTerms.indexOf(term) >= 0;
  }

  private toggle(term: string): void {
    const index = this.expandedTerms.indexOf(term);
    const next = this.expandedTerms.slice();
    if (index >= 0) {
      next.splice(index, 1);
    } else {
      next.push(term);
    }
    this.expandedTerms = next;
  }

  @Builder
  SummaryCard() {
    CardBox() {
      Row() {
        Stat({ label: '加权绩点', value: fmt(this.vm.weightedGpa, 2) })
        Stat({ label: '加权平均', value: fmt(this.vm.weightedScore, 1) })
        Stat({ label: '总学分', value: fmt(this.vm.totalCredit, 1) })
        Stat({ label: '门数', value: `${this.vm.grades.length}` })
      }
      .width('100%')

      if (this.vm.grades.some((g: Grade) => g.score === null)) {
        Text('含等级制课程（没有百分制分数），加权平均分只统计有分数的课程。')
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 10 })
      }
    }
  }

  /** 可点击的学期头：显示门数、均分，右侧箭头随展开状态旋转 */
  @Builder
  TermHeader(group: TermGrades) {
    Row() {
      Column() {
        Text(group.term)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.app_text_primary'))
        Text(this.termSubtitle(group))
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Text('∨')
        .fontSize(14)
        .fontColor($r('app.color.app_text_secondary'))
        .rotate({ angle: this.isExpanded(group.term) ? 180 : 0 })
        .animation({ duration: 200, curve: Curve.EaseInOut })
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .backgroundColor($r('app.color.app_cell_empty'))
    .borderRadius(12)
    .margin({ top: 4, bottom: 4 })
    .onClick(() => {
      this.toggle(group.term);
    })
  }

  private termSubtitle(group: TermGrades): string {
    const scores = group.list.filter((g) => g.score !== null);
    if (scores.length === 0) {
      return `${group.list.length} 门`;
    }
    const avg = scores.reduce((sum, g) => sum + (g.score ?? 0), 0) / scores.length;
    return `${group.list.length} 门 · 均分 ${fmt(avg, 1)}`;
  }
}

@Component
struct Stat {
  @StorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  label: string = '';
  value: string = '';

  build() {
    Column() {
      Text(this.value)
        .fontSize(20)
        .fontColor(this.themePrimary)
      Text(this.label)
        .fontSize(11)
        .fontColor($r('app.color.app_text_secondary'))
        .margin({ top: 2 })
    }
    .layoutWeight(1)
  }
}

@Component
struct GradeRow {
  grade: Grade = new Grade();

  build() {
    Row() {
      Column() {
        Text(this.grade.course)
          .fontSize(14)
          .fontColor($r('app.color.app_text_primary'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.grade.kind !== null) {
          Text(this.grade.kind ?? '')
            .fontSize(11)
            .fontColor($r('app.color.app_text_secondary'))
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Text(this.grade.level ?? fmt(this.grade.score, 0))
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.app_text_primary'))
        .padding({ left: 10, right: 10 })

      Text(`${fmt(this.grade.credit, 1)} 学分`)
        .fontSize(11)
        .fontColor($r('app.color.app_text_secondary'))
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 6, bottom: 6 })
  }
}
