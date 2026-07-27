#!/bin/sh
set -eu

jar_path="${1:?permissions plugin JAR path is required}"

if ! unzip -Z1 "$jar_path" | grep -qx \
    'gg/grounds/permissions/client/HttpPermissionRuntimeClient.class'; then
  echo "Permissions plugin verification failed (reason=missing_rest_client, jar=$jar_path)" >&2
  exit 1
fi

if unzip -Z1 "$jar_path" | grep -Eq \
    '^(gg/grounds/permissions/velocity/GrpcPermission|io/grpc/|com/google/protobuf/)'; then
  echo "Permissions plugin verification failed (reason=grpc_transport_present, jar=$jar_path)" >&2
  exit 1
fi

echo "Permissions plugin verification succeeded (jar=$jar_path)"
