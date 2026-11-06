interface CardRow {
  time: string;
  name: string;
  room: string;
  color: string;
}

/** 2x4 列表最多放的行数，超出折叠成「+N 节」 */
const MAX_ROWS: number = 4;

/**
 * 课表卡片。数据全部由 FormAbility / App 通过 formBindingData 推送进来，
 * 卡片自身不碰网络、不碰加密存储。
 * dim = "2*2" 显示概览（节数 + 下一节），"2*4" 显示今日课程列表。
 * 点击任意处拉起 App 到「今日」页。
 */
@Entry
@Component
struct ScheduleCard {
  @LocalStorageProp('state') readonly state: string = 'noSnapshot';
  @LocalStorageProp('dateLabel') readonly dateLabel: string = '';
  @LocalStorageProp('weekLabel') readonly weekLabel: string = '';
  @LocalStorageProp('countText') readonly countText: string = '';
  @LocalStorageProp('coursesJson') readonly coursesJson: string = '[]';
  @LocalStorageProp('nextJson') readonly nextJson: string = '';
  @LocalStorageProp('hint') readonly hint: string = '';
  @LocalStorageProp('themePrimary') readonly themePrimary: string = '#0A59F7';
  @LocalStorageProp('dim') readonly dim: string = '2*4';

  private rows(): CardRow[] {
    try {
      return JSON.parse(this.coursesJson) as CardRow[];
    } catch (e) {
      return [];
    }
  }

  private next(): CardRow | null {
    if (this.nextJson.length === 0) {
      return null;
    }
    try {
      return JSON.parse(this.nextJson) as CardRow;
    } catch (e) {
      return null;
    }
  }

  /** 「下一节 · 课名」；没有下一节时是空串 */
  private nextTitle(): string {
    const n = this.next();
    return n !== null ? `下一节 · ${n.name}` : '';
  }

  private nextSubtitle(): string {
    const n = this.next();
    return n !== null ? `${n.time} · ${n.room}` : '';
  }

  private openApp(): void {
    postCardAction(this, {
      action: 'router',
      abilityName: 'EntryAbility',
      params: { targetTab: 0 },
    });
  }

  @Builder
  Header() {
    Row() {
      Text(this.dateLabel)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.app_text_primary'))
      Blank()
      Text(this.weekLabel)
        .fontSize(11)
        .fontColor(this.themePrimary)
    }
    .width('100%')
  }

  @Builder
  CourseLine(row: CardRow) {
    Row() {
      Row()
        .width(3)
        .height(28)
        .borderRadius(2)
        .backgroundColor(row.color)
      Column() {
        Text(row.name)
          .fontSize(12)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.app_text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(`${row.time} · ${row.room}`)
          .fontSize(10)
          .fontColor($r('app.color.app_text_secondary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 2 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: 8 })
    }
    .width('100%')
    .margin({ top: 8 })
  }

  @Builder
  ListLayout() {
    Column() {
      this.Header()
      ForEach(this.rows().slice(0, MAX_ROWS), (row: CardRow) => {
        this.CourseLine(row)
      })
      if (this.rows().length > MAX_ROWS) {
        Text(`还有 ${this.rows().length - MAX_ROWS} 节，点击查看全部`)
          .fontSize(10)
          .fontColor(this.themePrimary)
          .margin({ top: 6 })
      }
    }
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  CompactLayout() {
    Column() {
      this.Header()
      Text(this.countText)
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.app_text_primary'))
        .margin({ top: 10 })
      if (this.nextTitle().length > 0) {
        Column() {
          Text(this.nextTitle())
            .fontSize(12)
            .fontColor($r('app.color.app_text_primary'))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(this.nextSubtitle())
            .fontSize(10)
            .fontColor($r('app.color.app_text_secondary'))
            .maxLines(1)
            .margin({ top: 2 })
        }
        .alignItems(HorizontalAlign.Start)
        .margin({ top: 6 })
      } else {
        Text('今天的课都上完啦')
          .fontSize(11)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 6 })
      }
    }
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  HintLayout(text: string) {
    Column() {
      Text(text.length > 0 ? text : '打开 App 同步课表')
        .fontSize(12)
        .fontColor($r('app.color.app_text_secondary'))
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  build() {
    Stack() {
      if (this.state === 'ok') {
        if (this.dim === '2*2') {
          this.CompactLayout()
        } else {
          this.ListLayout()
        }
      } else if (this.state === 'empty') {
        Column() {
          this.Header()
          this.HintLayout('今天没课')
        }
      } else {
        this.HintLayout(this.hint)
      }
    }
    .width('100%')
    .height('100%')
    .padding(14)
    .backgroundColor($r('app.color.app_card_bg'))
    .onClick(() => {
      this.openApp();
    })
  }
}
