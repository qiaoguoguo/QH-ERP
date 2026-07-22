function Get-DemoMinioCredentialShellPrefix {
    return 'if [ -r ${MINIO_ROOT_USER_FILE:-/__qherp_missing_minio_user_secret__} ]; then MINIO_ROOT_USER=$(cat ${MINIO_ROOT_USER_FILE}); fi; if [ -r ${MINIO_ROOT_PASSWORD_FILE:-/__qherp_missing_minio_password_secret__} ]; then MINIO_ROOT_PASSWORD=$(cat ${MINIO_ROOT_PASSWORD_FILE}); fi; export MINIO_ROOT_USER MINIO_ROOT_PASSWORD'
}
