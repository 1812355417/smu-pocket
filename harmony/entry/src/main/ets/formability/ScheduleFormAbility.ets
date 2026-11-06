import { FormExtensionAbility, formBindingData, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { DEFAULT_THEME } from '../core/ThemeStore';
import { readFormMap, writeFormMap } from '../core/WidgetBridge';
import { WidgetSnapshotStore, WidgetFormData, emptyFormData, summarizeForNow } from '../core/WidgetSnapshot';

const TAG: string = 'SMUPocket';
const DOMAIN: number = 0x0a06;

/** formParam.FormParam 在此 SDK 的 FormKit 里不导出，直接用常量字符串 */
const KEY_FORM_IDENTITY: string = 'ohos.extra.param.key.form_identity';
const KEY_FORM_DIMENSION: string = 'ohos.extra.param.key.form_dimension';

/** DIMENSION_KEY 给的是数字：1=1x2 2=2x2 3=2x4 4=4x4；本卡片只声明了 2x2 / 2x4 */
function dimLabel(raw: Object | undefined): string {
  return raw?.toString() === '2' ? '2*2' : '2*4';
}

/**
 * 课表卡片（独立进程）。两职责：
 * - 维护 formId → 尺寸登记表（preferences，App 主进程推送时按尺寸发数据）
 * - 系统定时刷新（form_config 的 updateDuration）时从快照自算当天课程，
 *   跨天/跨周不依赖 App 被打开
 */
export default class ScheduleFormAbility extends FormExtensionAbility {
  onAddForm(want: Want): formBindingData.FormBindingData {
    const formId = want.parameters?.[KEY_FORM_IDENTITY]?.toString() ?? '';
    const dim = dimLabel(want.parameters?.[KEY_FORM_DIMENSION]);
    if (formId.length > 0) {
      const forms = readFormMap(this.context);
      forms.set(formId, dim);
      writeFormMap(this.context, forms);
    }
    return formBindingData.createFormBindingData(this.buildPayload(dim));
  }

  onUpdateForm(formId: string): void {
    const dim = readFormMap(this.context).get(formId) ?? '2*4';
    try {
      formProvider.updateForm(formId, formBindingData.createFormBindingData(this.buildPayload(dim)));
    } catch (e) {
      hilog.warn(DOMAIN, TAG, `定时刷新失败 formId=${formId}: ${JSON.stringify(e)}`);
    }
  }

  onRemoveForm(formId: string): void {
    const forms = readFormMap(this.context);
    forms.delete(formId);
    writeFormMap(this.context, forms);
  }

  private buildPayload(dim: string): WidgetFormData {
    const snapshot = new WidgetSnapshotStore(this.context).read();
    const data: WidgetFormData = snapshot === null
      ? emptyFormData('打开 App 同步课表', DEFAULT_THEME.primary)
      : summarizeForNow(snapshot, new Date());
    data.dim = dim;
    return data;
  }
}
