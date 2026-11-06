#!/usr/bin/env bash
# 鸿蒙逻辑层离线验证（不需要 DevEco Studio / 模拟器）：
# 把 core/data/viewmodel 的 .ets 复制为 .ts，用 tsc 编译成 JS，
# 再跑 test/run-tests.cjs —— 断言镜像 Android 端单测，复用同一份脱敏抓包 fixture。
#
# 依赖：node ≥ 18（首次运行会通过 npx 拉取 typescript）
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
WORK="$HERE/.work"
SRC_ETS="$REPO/harmony/entry/src/main/ets"

rm -rf "$WORK"
mkdir -p "$WORK/src"

# 只测不依赖 ArkUI 的逻辑层（UI 组件需要真机/模拟器验证）
for dir in core data viewmodel; do
  mkdir -p "$WORK/src/$dir"
  for f in "$SRC_ETS/$dir"/*.ets; do
    cp "$f" "$WORK/src/$dir/$(basename "${f%.ets}").ts"
  done
done

cp "$HERE/kits.d.ts" "$WORK/kits.d.ts"

# @Observed 是 ArkUI 编译器处理的装饰器，tsc 环境下直接剥掉即可（不影响逻辑）
sed -i 's/^@Observed$//' "$WORK/src/viewmodel"/*.ts

cat > "$WORK/tsconfig.json" <<'EOF'
{
  "compilerOptions": {
    "target": "ES2021",
    "module": "CommonJS",
    "strict": false,
    "outDir": "out",
    "rootDir": "src",
    "skipLibCheck": true,
    "experimentalDecorators": true,
    "lib": ["ES2021", "DOM"],
    "types": []
  },
  "include": ["kits.d.ts", "src/**/*.ts"]
}
EOF

(cd "$WORK" && npx --yes -p typescript tsc -p tsconfig.json)

# 运行时的 @kit.* 桩（只保证 import 不炸，测试不触碰平台能力）
mkdir -p "$WORK/out/node_modules"
cp -r "$HERE/stubs/." "$WORK/out/node_modules/"

REPO_ROOT="$REPO" node "$HERE/run-tests.cjs" "$WORK/out"
