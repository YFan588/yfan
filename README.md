# yfan
230205306楚一帆计网课设
# 反向TCP服务器和客户端程序文档

## 1. 概述
本项目包含一个TCP服务器（`reversetcpserver.java`）和一个TCP客户端（`reversetcpclient.java`）。该系统的主要功能是从客户端读取一个文本文件，将其分割成若干数据块，将这些数据块发送到服务器，在服务器端将每个数据块的内容反转，然后将反转后的数据块发送回客户端。最后，客户端将这些反转后的数据块组合起来，生成一个反转后的文本文件。

## 2. 运行环境
- **Java开发工具包（JDK）**：此程序使用Java开发，因此您需要在系统上安装JDK。建议使用JDK 8或更高版本。
- **操作系统**：此程序具有平台独立性，可以在Windows、Linux或macOS上运行。

## 3. 编译
要编译服务器和客户端程序，请打开终端，导航到包含`.java`文件的目录，然后运行以下命令：
```bash
javac reversetcpserver.java
javac reversetcpclient.java
```

## 4. 服务器程序（`reversetcpserver.java`）

### 4.1 配置选项
服务器程序需要一个端口号作为命令行参数：
```bash
java reversetcpserver <端口号>
```
- `<端口号>`：服务器监听的端口号。它应该是1到65535之间的整数。例如，如果您希望服务器监听8888端口，可以运行以下命令：
```bash
java reversetcpserver 8888
```

### 4.2 功能
- 服务器使用线程池来并发处理多个客户端连接。
- 它首先等待客户端连接。
- 客户端连接后，它读取客户端发送的初始化包。
- 如果初始化包有效，它向客户端发送一个协议包。
- 然后，它处理客户端发送的每个数据块，反转数据块的内容，并将反转后的数据块发送回客户端。

## 5. 客户端程序（`reversetcpclient.java`）

### 5.1 配置选项
客户端程序需要五个命令行参数：
```bash
java reversetcpclient <服务器IP> <服务器端口> <文件名> <Lmin> <Lmax>
```
- `<服务器IP>`：服务器的IP地址。它可以是IPv4地址（例如，本地机器的`127.0.0.1`）。
- `<服务器端口>`：服务器监听的端口号。
- `<文件名>`：要反转的文本文件的路径。
- `<Lmin>`：每个数据块的最小长度。它应该是一个正整数。
- `<Lmax>`：每个数据块的最大长度。它应该是一个大于或等于`<Lmin>`的正整数。

例如，如果服务器运行在`127.0.0.1`的`8888`端口上，并且您想要反转文件`test.txt`，每个数据块的最小长度为`10`，最大长度为`20`，您可以运行以下命令：
```bash
java reversetcpclient 127.0.0.1 8888 test.txt 10 20
```

### 5.2 功能
- 客户端读取指定的文本文件。
- 它将文件内容分割成若干数据块，每个数据块的长度在`<Lmin>`和`<Lmax>`之间。
- 它向服务器发送一个初始化包，指示数据块的数量。
- 收到服务器的协议包后，它将每个数据块发送到服务器并接收反转后的数据块。
- 最后，它将所有反转后的数据块组合起来，并将它们保存为一个新文件，文件后缀为`.reversed`。

## 6. 错误处理
- 服务器和客户端程序都处理常见的I/O异常，并在发生错误时打印错误消息。
- 如果客户端从服务器收到无效的数据包，它将打印错误消息并终止连接。
- 如果服务器从客户端收到无效的数据包，它将打印错误消息并停止处理客户端的请求。
