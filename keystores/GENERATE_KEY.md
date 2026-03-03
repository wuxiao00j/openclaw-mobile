# 生成签名密钥

由于当前环境缺少 JDK，请手动创建签名密钥。

## 方法 1: 使用 Android Studio

1. 打开项目
2. Build → Generate Signed Bundle / APK
3. Create new...
4. 填写密钥信息并创建

## 方法 2: 命令行 (需要安装 JDK)

```bash
cd OpenClawMobile/keystores

# 生成密钥
keytool -genkey -v \
    -keystore openclaw-release.keystore \
    -alias openclaw \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000

# 创建配置文件
cat > keystore.properties << EOF
storeFile=keystores/openclaw-release.keystore
storePassword=你的密码
keyAlias=openclaw
keyPassword=你的密码
EOF
```

## 方法 3: 使用 Debug 签名测试

如果暂时没有发布密钥，可以直接构建 debug 版本进行测试：

```bash
./gradlew assembleDebug
```

Debug 版本使用 Android SDK 自带的 debug 签名。
