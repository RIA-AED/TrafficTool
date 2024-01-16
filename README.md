# TrafficTool

Velocity 玩家流量整型和统计工具。

## 功能

* 玩家粒度的流量整形（限速）
* 服务器粒度的流量整型（进程限速）
* 查看实时流量
* 使用命令动态修改整型参数（仅本次会话有效）

## 命令

所有命令的实际参数以示例为准，如果输入后提示参数错误（不足），请在后面随意追加几个参数，将其补满 2 个参数。

* /traffic view global - 浏览Velocity上的实时流量数据
* /traffic view global <player> - 浏览指定玩家的实时流量数据
* /traffic upload - 手动上传当前实时流量数据到数据库用作分析
* /traffic config global <连接最大写速度> <连接最大读速度> - 设置Velocity网络的流量整形（可以理解为进程限速）
* /traffic config player <用户名> <连接最大写速度> <连接最大读速度> - 设置指定玩家的本次连接的流量整形
* /traffic me - 查看自己的连接参数

## 示例返回

`/traffic view global`:

```
> traffic view global
[21:18:14 INFO]: 检查间隔时间: 1000ms
[21:18:14 INFO]: 最大等待时长: 15000
[21:18:14 INFO]: 最大写入延迟: 4000
[21:18:14 INFO]: 最大写入大小: 4.00 MiB
[21:18:14 INFO]: 读速率限制: 0 B/1000ms
[21:18:14 INFO]: 写速率限制: 0 B/1000ms
[21:18:14 INFO]: 队列大小: 0
[21:18:14 INFO]: -----
[21:18:14 INFO]: 累计读取字节数: 0 B
[21:18:14 INFO]: 累计写入字节数: 43.90 GiB
[21:18:14 INFO]: 当前读取字节数: 0 B
[21:18:14 INFO]: 当前写入字节数: 243.98 KiB
[21:18:14 INFO]: 实际写入吞吐量: 1.85 MiB/1000ms
[21:18:14 INFO]: 实际写入字节数: 244.34 KiB
[21:18:14 INFO]: 最后累计时间: 1705343672607
[21:18:14 INFO]: 最后读取字节数: 0 B
[21:18:14 INFO]: 最后读取吞吐量: 0 B/1000ms
[21:18:14 INFO]: 最后写入吞吐量: 1.85 MiB/1000ms
[21:18:14 INFO]: 最后写入字节数: 1.85 MiB
```

`/traffic view player <player>`:

```
> traffic view player Nanako_1
[21:20:14 INFO]: 检查间隔时间: 1000ms
[21:20:14 INFO]: 最大等待时长: 15000
[21:20:14 INFO]: 最大写入延迟: 4000
[21:20:14 INFO]: 最大写入大小: 4.00 MiB
[21:20:14 INFO]: 读速率限制: 0 B/1000ms
[21:20:14 INFO]: 写速率限制: 1.45 MiB/1000ms
[21:20:14 INFO]: 队列大小: 0 (长时间或过多的包堆积将导致 Ping 升高)
[21:20:14 INFO]: -----
[21:20:14 INFO]: 累计读取字节数: 0 B
[21:20:14 INFO]: 累计写入字节数: 274.62 MiB
[21:20:14 INFO]: 当前读取字节数: 0 B
[21:20:14 INFO]: 当前写入字节数: 1.31 KiB
[21:20:14 INFO]: 实际写入吞吐量: 2.63 KiB/1000ms
[21:20:14 INFO]: 实际写入字节数: 1.31 KiB
[21:20:14 INFO]: 最后累计时间: 1705408771169
[21:20:14 INFO]: 最后读取字节数: 0 B
[21:20:14 INFO]: 最后读取吞吐量: 0 B/1000ms
[21:20:14 INFO]: 最后写入吞吐量: 2.63 KiB/1000ms
[21:20:14 INFO]: 最后写入字节数: 2.63 KiB
```

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/5dafc582-cb7c-4af2-97a8-f1ca605edfba)

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/75800b17-88f2-427b-9a03-1770da856584)

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/7be10ed5-78ed-4a8d-8402-2bda0f49a712)

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/982ed19c-1298-4987-9787-1988cedb42a3)

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/9e57c504-acc9-453f-9d84-d2499a14f7ec)

![image](https://github.com/RIA-AED/TrafficTool/assets/30802565/881cf176-ad8c-4763-be63-cb6601612bea)


## 注意事项

* 数据库默认没有索引，需要手动设置
* 数据库没有自动清理，请自己手动及时清理，避免爆硬盘

## 示例配置文件

```yaml
global-traffic-handler:
  scheduled-thread-pool-core-pool-size: 2
  check-interval: 1000
channel-traffic-handler:
  check-interval: 1000


player-traffic-shaping:
  writeLimit: 1520435
  readLimit: 0 # 0为不限制

send-traffic-rule-update-notification: true

ignored-servers:
  - lobby


# 数据库配置
database:
  # 使用 MySQL 存储数据，false 则直接崩溃
  mysql: true
  # 下面的配置应该就不用说了吧
  host: localhost
  port: 3306
  database: mytraffic
  user: trafficdb
  password: password
  # 表前缀
  prefix: "myserver_"
  # 使用 SSL
  usessl: false
  # 额外配置
  properties:
    connection-timeout: 60000
    validation-timeout: 3000
    idle-timeout: 60000
    login-timeout: 10
    maxLifeTime: 60000
    maximum-pool-size: 8
    minimum-idle: 2
    cachePrepStmts: true
    prepStmtCacheSize: 250
    prepStmtCacheSqlLimit: 2048
    useUnicode: true
    characterEncoding: utf8
    allowPublicKeyRetrieval: true
```


