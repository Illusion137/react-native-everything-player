#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_ROOT="${ROOT_DIR}/vendor/googlevideo/protos"
OUT_DIR="${ROOT_DIR}/cpp/sabr/proto"

if ! command -v protoc >/dev/null 2>&1; then
  echo "protoc is required to generate SABR protobuf headers." >&2
  exit 1
fi

if [[ ! -d "${PROTO_ROOT}" ]]; then
  echo "Missing ${PROTO_ROOT}. Clone googlevideo into vendor/googlevideo first." >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

PROTO_FILES=()
EXCLUDED_FILES=(
  "video_streaming/onesie_proxy_status.proto"
  "video_streaming/ump_part_id.proto"
)
while IFS= read -r file; do
  rel="${file#${PROTO_ROOT}/}"
  skip=false
  for excluded in "${EXCLUDED_FILES[@]}"; do
    if [[ "${rel}" == "${excluded}" ]]; then
      skip=true
      break
    fi
  done
  if [[ "${skip}" == true ]]; then
    continue
  fi
  PROTO_FILES+=("${file}")
done < <(find "${PROTO_ROOT}" -name '*.proto' -type f | sort)

if [[ ${#PROTO_FILES[@]} -eq 0 ]]; then
  echo "No .proto files found in ${PROTO_ROOT}." >&2
  exit 1
fi

protoc --proto_path="${PROTO_ROOT}" --cpp_out="${OUT_DIR}" "${PROTO_FILES[@]}"

echo "Generated SABR protobuf headers into ${OUT_DIR}"
