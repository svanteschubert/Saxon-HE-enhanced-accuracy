#!/bin/sh
# Run from the repository root or from an extracted release ZIP.
set -eu

example_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$example_root/pom.xml" ]; then
    example_dir="$example_root/target"
else
    example_dir="$example_root"
fi

case "${1:-valid}" in
    valid)
        invoice=invoice-2017-ubl.xml
        report=invoice-validation.svrl.xml
        expected='Expected: PASS (no failed assertions).'
        ;;
    invalid)
        invoice=variants/vat-basis-1-too-low-ubl.xml
        report=invalid-invoice-validation.svrl.xml
        expected='Expected: FAIL with BR-S-08 (the deliberately invalid invoice).'
        ;;
    *)
        echo "Usage: sh $0 [valid|invalid]" >&2
        exit 1
        ;;
esac
if [ "$#" -gt 1 ]; then
    echo "Usage: sh $0 [valid|invalid]" >&2
    exit 1
fi

set -- "$example_dir"/Saxon-HE-accuracy-*-standalone.jar
if [ ! -f "$1" ] || [ "$#" -ne 1 ]; then
    echo "Expected one standalone JAR in $example_dir; found none or multiple versions." >&2
    echo 'From the repository root, run: mvn clean install' >&2
    echo 'Then run this example again.' >&2
    exit 1
fi
example_jar=$1
example_files="$example_dir/examples/en16931"
for example_file in "$invoice" EN16931-UBL-validation.xslt summarize-svrl.xsl; do
    if [ ! -f "$example_files/$example_file" ]; then
        echo "Example file missing: $example_files/$example_file" >&2
        echo 'From the repository root, run: mvn clean install' >&2
        exit 1
    fi
done

printf 'Standalone JAR: %s\n%s\n' "$example_jar" "$expected"
java -jar "$example_jar" "-s:$example_files/$invoice" \
    "-xsl:$example_files/EN16931-UBL-validation.xslt" "-o:$example_dir/$report"
printf 'SVRL report: %s\n' "$example_dir/$report"
java -jar "$example_jar" "-s:$example_dir/$report" "-xsl:$example_files/summarize-svrl.xsl"
