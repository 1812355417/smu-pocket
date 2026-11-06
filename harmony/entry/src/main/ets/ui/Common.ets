/**
 * 共享小组件与工具函数（对应 Android 端 ui/components/）。
 */

const DAY_CHARS: string = '一二三四五六日';

/** 1..7 → 一..日 */
export function cn(dayOfWeek: number): string {
  return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAY_CHARS.charAt(dayOfWeek - 1) : '?';
}

/** null 统一显示成「—」，绝不显示 0 —— 0 分和「没有这项」是两件事 */
export function fmt(value: number | null, digits: number): string {
  if (value === null) {
    return '—';
  }
  return value.toFixed(digits);
}

/** 教务把 12 个老师的名字拼在一个字段里，卡片上只显示前 3 个 */
export function shortTeachers(raw: string | null): string | null {
  if (raw === null) {
    return null;
  }
  const names = raw.split(/[,，、]/).map((s) => s.trim()).filter((s) => s.length > 0);
  if (names.length === 0) {
    return null;
  }
  if (names.length <= 3) {
    return names.join('、');
  }
  return `${names.slice(0, 3).join('、')} 等${names.length}人`;
}

/** 解析教务给的 "#1f86c7"，解析失败返回 null */
export function parseHexColor(hex: string | null): string | null {
  if (hex === null) {
    return null;
  }
  const t = hex.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(t) || /^#[0-9a-fA-F]{8}$/.test(t)) {
    return t;
  }
  return null;
}

/** 亮色块配深字，深色块配白字 —— 教务给的配色有深有浅，不能写死 */
export function contentColorFor(bgHex: string): string {
  const m = /^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})/.exec(bgHex);
  if (!m) {
    return '#FFFFFF';
  }
  const r = parseInt(m[1], 16) / 255;
  const g = parseInt(m[2], 16) / 255;
  const b = parseInt(m[3], 16) / 255;
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  return luminance > 0.55 ? '#1B1B1B' : '#FFFFFF';
}

/** 页面标题头 */
@Component
export struct ScreenHeader {
  title: string = '';
  subtitle: string = '';

  build() {
    Column() {
      Text(this.title)
        .fontSize(24)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.app_text_primary'))
      if (this.subtitle.length > 0) {
        Text(this.subtitle)
          .fontSize(13)
          .fontColor($r('app.color.app_text_secondary'))
          .margin({ top: 2 })
      }
    }
    .alignItems(HorizontalAlign.Start)
    .width('100%')
    .padding({ left: 20, right: 20, top: 20, bottom: 8 })
  }
}

/** 居中空态提示 */
@Component
export struct EmptyHint {
  text: string = '';

  build() {
    Column() {
      Text(this.text)
        .fontSize(14)
        .fontColor($r('app.color.app_text_secondary'))
    }
    .width('100%')
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
    .padding(24)
  }
}

/**
 * 卡片容器：内容通过 @BuilderParam 传入。
 *
 * 外边距必须用「外层 padding」做，不能写成 width('100%') + margin：
 * ArkUI 里 margin 在组件宽度的**外面**，100% 宽再加左右 margin 会把右边顶出屏幕。
 */
@Component
export struct CardBox {
  @BuilderParam content: () => void = this.defaultContent;

  @Builder
  defaultContent() {
  }

  build() {
    Column() {
      Column() {
        this.content()
      }
      .alignItems(HorizontalAlign.Start)
      .width('100%')
      .padding(16)
      .backgroundColor($r('app.color.app_card_bg'))
      .borderRadius(16)
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 6, bottom: 6 })
  }
}

/** 左标签右值的一行 */
@Component
export struct LabelValue {
  label: string = '';
  value: string = '';

  build() {
    Row() {
      Text(this.label)
        .fontSize(14)
        .fontColor($r('app.color.app_text_secondary'))
        .width(96)
      Text(this.value)
        .fontSize(14)
        .fontColor($r('app.color.app_text_primary'))
        .layoutWeight(1)
    }
    .width('100%')
    .margin({ top: 2, bottom: 2 })
  }
}

/**
 * 「自主学习」标签。compact 模式用于课表网格（格子窄，只放「自主」）。
 * 数据来源：教务 jxhjmc = 自主学习，实测全部没有教室。
 */
@Component
export struct SelfStudyChip {
  compact: boolean = false;

  build() {
    Text(this.compact ? '自主' : '自主学习')
      .fontSize(10)
      .fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.app_self_study'))
      .backgroundColor('#242E7D32')
      .borderRadius(4)
      .padding({ left: 5, right: 5, top: 1, bottom: 1 })
  }
}
