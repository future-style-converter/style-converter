#!/bin/bash
# tools/titan/results/wave53-soft-hyphen/skeptic-evidence/revert-units.sh — wave 53 L2 SKEPTIC. Revert-unit isolation in the skeptic export tree: arm F1-only (F2 files at HEAD, new F2 files absent, seam-2 off) and
# arm F2-only (F1 files at HEAD), each running the typography.* + core.renderer.* Compose packages; restores after.
set -u
S=${1:?usage: revert-units.sh <dir holding the export tree exp/ (git archive HEAD + L2 files + seam-2)>}
E=$S/exp; T=$(cd "$(dirname "$0")/../../../../.." && pwd); B=$S/unitbak
F2MOD=(runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt runtimes/compose/src/test/java/com/styleconverter/runtime/typography/inline/InlineRunFoldTest.kt runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt)
F2NEW=(runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InertOutOfFlowMember.kt runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/RunFoldBreadcrumbSeamTest.kt)
F1MOD=(runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipelineTest.kt)
rm -rf $B; for f in "${F2MOD[@]}" "${F2NEW[@]}" "${F1MOD[@]}"; do mkdir -p $B/$(dirname $f); cp $E/$f $B/$f; done
before=$(cd $E && shasum -a 256 "${F2MOD[@]}" "${F2NEW[@]}" "${F1MOD[@]}")
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
runarm() {
  rm -f $E/runtimes/compose/build/test-results/testDebugUnitTest/*.xml
  (cd $E/apps/android-harness && ./gradlew --no-daemon -q -Pkotlin.compiler.execution.strategy=in-process :runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.typography.*' --tests 'com.styleconverter.runtime.core.renderer.*' > $S/g-$1.log 2>&1); echo "$1 exit $?"
  python3 - "$E" "$1" <<'PY'
import glob,sys,xml.etree.ElementTree as ET
E,arm=sys.argv[1:]; n=f=0; bad=[]
for x in glob.glob(E+'/runtimes/compose/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(x).getroot(); n+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors'))
    bad+=[tc.get('classname').rsplit('.',1)[-1]+'.'+tc.get('name') for tc in r.iter('testcase') if tc.find('failure') is not None or tc.find('error') is not None]
print(arm,'total',n,'fail',f,bad)
PY
}
# arm F1-only
for f in "${F2MOD[@]}"; do git -C $T show HEAD:$f > $E/$f; done; for f in "${F2NEW[@]}"; do rm $E/$f; done
runarm F1-only
for f in "${F2MOD[@]}" "${F2NEW[@]}"; do cp $B/$f $E/$f; done
# arm F2-only
for f in "${F1MOD[@]}"; do git -C $T show HEAD:$f > $E/$f; done
runarm F2-only
for f in "${F1MOD[@]}"; do cp $B/$f $E/$f; done
after=$(cd $E && shasum -a 256 "${F2MOD[@]}" "${F2NEW[@]}" "${F1MOD[@]}")
[ "$before" == "$after" ] && echo "export tree restored byte-exact" || echo "RESTORE MISMATCH"
