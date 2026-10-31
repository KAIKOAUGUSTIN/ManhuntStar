#!/usr/bin/env bash
# Monta as notas de uma versão a partir dos commits do PR que entrou na main e põe a versão no
# topo do CHANGELOG.md.
#
# Uso: notas-da-versao.sh <versão> <versão anterior> <commits.tsv> <saída.md>
# commits.tsv: uma linha por commit, "sha<TAB>login do autor<TAB>primeira linha da mensagem".
set -euo pipefail

versao="$1"
anterior="$2"
commits="$3"
saida="$4"
repo="${GITHUB_REPOSITORY:-}"

novidades=""
dependencias=""
declare -A vistos=()
while IFS=$'\t' read -r sha autor mensagem; do
    [[ -z "$sha" || -z "$mensagem" ]] && continue
    # commits de versão do próprio workflow não entram
    [[ "$autor" == "github-actions[bot]" ]] && continue
    [[ -n "${vistos[$mensagem]:-}" ]] && continue
    vistos[$mensagem]=1
    linha="- ${mensagem} (${sha:0:7})"
    if [[ "$autor" == "dependabot[bot]" || "$mensagem" == Bump\ * ]]; then
        dependencias+="${linha}"$'\n'
    else
        novidades+="${linha}"$'\n'
    fi
done < "$commits"

paper=$(sed -n "s/^api-version: *'\{0,1\}\([^']*\)'\{0,1\}$/\1/p" src/main/resources/plugin.yml)

{
    if [[ -n "$novidades" ]]; then
        printf '### Novidades\n\n%s\n' "$novidades"
    fi
    if [[ -n "$dependencias" ]]; then
        printf '### Dependências\n\n%s\n' "$dependencias"
    fi
    if [[ -z "$novidades" && -z "$dependencias" ]]; then
        printf 'Sem mudanças listadas.\n\n'
    fi
    printf '**Paper:** %s · **Java:** 25\n' "$paper"
    if [[ -n "$repo" ]] && git rev-parse -q --verify "refs/tags/v${anterior}" > /dev/null; then
        printf '\n**Tudo desde a %s:** https://github.com/%s/compare/v%s...v%s\n' "$anterior" "$repo" "$anterior" "$versao"
    fi
} > "$saida"

# CHANGELOG.md: a versão nova entra antes da primeira "## " (a mais recente fica em cima)
secao=$(printf '## %s - %s\n\n%s\n' "$versao" "$(date -u +%Y-%m-%d)" "$(cat "$saida")")
if [[ ! -f CHANGELOG.md ]]; then
    printf '# Changelog\n\nGerado automaticamente a cada merge na main.\n\n' > CHANGELOG.md
fi
SECAO="$secao" awk '
    !feito && /^## / { print ENVIRON["SECAO"]; print ""; feito = 1 }
    { print }
    END { if (!feito) { print ENVIRON["SECAO"] } }
' CHANGELOG.md > CHANGELOG.md.novo
mv CHANGELOG.md.novo CHANGELOG.md
