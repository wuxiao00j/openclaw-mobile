# 签名密钥

此目录用于存放应用签名密钥。

## 创建发布密钥

```bash
cd OpenClawMobile/keystores
keytool -genkey -v \
    -keystore openclaw-release.keystore \
    -alias openclaw \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000
```

## 密钥信息

- **密钥库文件**: `openclaw-release.keystore`
- **别名**: `openclaw`
- **有效期**: 10000 天

**注意**: 请妥善保管密钥库文件和密码，丢失后无法更新应用！
