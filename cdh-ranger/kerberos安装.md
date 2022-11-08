## kerberos安装

### kerberos server安装

```shell
$ yum install krb5-server  krb5-workstation krb5-libs
```

**krb5.conf 配置**

/etc/krb5.conf

客户端根据该文件中的信息去访问KDC

```properties
# To opt out of the system crypto-policies configuration of krb5, remove the
# symlink at /etc/krb5.conf.d/crypto-policies which will not be recreated.
includedir /etc/krb5.conf.d/

#Kerberos守护进程的日志记录方式。换句话说，表示 server 端的日志的打印位置。
[logging]
 #默认的krb5libs.log日志文件存放路径
 default = FILE:/var/log/krb5libs.log
 #默认的krb5kdc.log日志文件存放路径
 kdc = FILE:/var/log/krb5kdc.log
 #默认的kadmind.log日志文件存放路径
 admin_server = FILE:/var/log/kadmind.log

#Kerberos使用的默认值，当进行身份验证而未指定Kerberos域时，则使用default_realm参数指定的Kerberos域。即每种连接的默认配置，需要注意以下几个关键的配置：
[libdefaults]
 #DNS查找域名，我们可以理解为DNS的正向解析，该功能我没有去验证过，默认禁用。（我猜测该功能和domain_realm配置有关）
 dns_lookup_realm = false
 # 凭证生效的时限，设置为24h。
 ticket_lifetime = 24h
 # 凭证最长可以被延期的时限，一般为7天。当凭证过期之后，对安全认证的服务的后续访问则会失败。
 renew_lifetime = 7d
 # 如果此参数被设置为true，则可以转发票据，这意味着如果具有TGT的用户登陆到远程系统，则KDC可以颁发新的TGT，而不需要用户再次进行身份验证。
 forwardable = true
 # 我理解是和dns_lookup_realm相反，即反向解析技术，该功能我也没有去验证过，默认禁用即可。
 rdns = false
 # 在KDC中配置pkinit的位置，该参数的具体功能我没有做进一步验证。
 pkinit_anchors = /etc/pki/tls/certs/ca-bundle.crt
 #设置 Kerberos 应用程序的默认领域。如果您有多个领域，只需向 [realms] 节添加其他的语句。其中默认EXAMPLE.COM可以为任意名字,推荐为大写，这里我改成了HADOOP.COM。必须跟要配置的realm的名称一致。
 default_realm = HADOOP.COM
 # 顾名思义，默认的缓存名称，不推荐使用该参数。
 # default_ccache_name = KEYRING:persistent:%{uid}

[realms]
 HADOOP.COM = {
  # kdc服务器地址。格式  [主机名或域名]:端口， 默认端口是88，默认端口可不写
  kdc = server.kerberos.com:88
  #  # admin服务地址 格式 [主机名或域名]:端口， 默认端口749，默认端口可不写
  admin_server = server.kerberos.com:749
  # 代表默认的域名，设置Server主机所对应的域名
  default_domain = kerberos.com
 }
 
#指定DNS域名和Kerberos域名之间映射关系。指定服务器的FQDN，对应的domain_realm值决定了主机所属的域。
[domain_realm]
 .kerberos.com = HADOOP.COM
  kerberos.com = HADOOP.COM

#kdc的配置信息。即指定kdc.conf的位置。
[kdc]
 # kdc的配置文件路径，默认没有配置，如果是默认路径，可以不写
 profile = /var/kerberos/krb5kdc/kdc.conf
```



**kdc.conf**

/var/kerberos/krb5kdc/kdc.conf

kdc专属的配置服务

```properties
[kdcdefaults]
 #指定KDC的默认端口
 kdc_ports = 88
 # 指定KDC的TCP协议默认端口。
 kdc_tcp_ports = 88

[realms]
 #该部分列出每个领域的配置。
 HADOOP.COM = {
  #和 supported_enctypes 默认使用 aes256-cts。由于，JAVA 使用 aes256-cts 验证方式需要安装额外的 jar 包（后面再做说明）。推荐不使用，并且删除 aes256-cts。（建议注释掉，默认也是注释掉的）
  #master_key_type = aes256-cts
  #标注了 admin 的用户权限的文件，若文件不存在，需要用户自己创建。即该参数允许为具有对Kerberos数据库的管理访问权限的UPN指定ACL。
  acl_file = /var/kerberos/krb5kdc/kadm5.acl
   #该参数指向包含潜在可猜测或可破解密码的文件。
  dict_file = /usr/share/dict/words
  #KDC 进行校验的 keytab。
  admin_keytab = /var/kerberos/krb5kdc/kadm5.keytab
  # ticket 的默认生命周期为24h
  max_file = 24h
  # #该参数指定在多长时间内可重获取票据，默认为0
  max_renewable_life = 7d
  #指定此KDC支持的各种加密类型。
  supported_enctypes = aes256-cts:normal aes128-cts:normal des3-hmac-sha1:normal arcfour-hmac:normal camellia256-cts:normal camellia128-cts:normal des-hmac-sha1:normal des-cbc-md5:normal des-cbc-crc:normal
 }

```

**kadm5.acl**

/var/kerberos/krb5kdc/kadm5.acl

kdc权限相关配置

**初始化kdc数据库**

```shell
kdb5_util create -r HADOOP.COM -s
ll -a /var/kerberos/krb5kdc/
```

**启停kerberos**

```shell
$ systemctl start krb5kdc kadmin
$ systemctl stop krb5kdc kadmin
$ systemctl status krb5kdc kadmin
```

**kadmin.local**

**添加用户**

```shell
#交互式
$ kadmin.local
$ addprinc -randkey rangeradmin/node02
$ addprinc -pw 123456 rangeradmin/node02
$ list_principals
```

```shell
$ kadmin.local -q "add_principal rangeradmin/node02" #非交互式
```

**导出keytab**

```shell
#交互式
$ kadmin.local
$ ktadd -norandkey -kt /opt/rangeradmin.service.keytab HTTP/node02@TAIZHI.COM rangeradmin/node02@TAIZHI.COM
```

```shell
#非交互式
$ kadmin.local -q "ktadd -norandkey -k /opt/rangeradmin.service.keytab rangeradmin/node02"
```

```shell
#查看keytab
$ klist -kt /opt/rangeradmin.service.keytab
```



### kerberos client安装

```shell
$ yum install krb5-devel krb5-workstation -y
```

**krb5.conf**

/etc/krb5.conf

客户端根据该文件中的信息取访问KDC,配置文件参考同上

```SHELL
 $ kadmin -p test@HADOOP.COM #指定用户连接kdc server
```

```shell
$ kinit rangeradmin/node02@TAIZHI.COM#密码方式认证
$ kinit -kt /opt/security/keytab/rangeradmin.service.keytab rangeradmin/node02 #keytab认证
$ klist
```



### kerberos常用命令

**kadmin.local:**

| 操作                                       | 描述             | 示例                                       |
| ---------------------------------------- | -------------- | ---------------------------------------- |
| add_principal, addprinc, ank             | 增加 principal   | addprinc -randkey rangeradmin/node02，addprinc -pw 123456 rangeradmin/node02 |
| cpw                                      | 修改密码           | cpw test@HADOOP.COM                      |
| delete_principal, delprinc               | 删除 principal   | delete_principal test@HADOOP.COM         |
| modify_principal, modprinc               | 修改 principal   | modify_principal test@HADOOP.COM         |
| rename_principal, renprinc               | 重命名 principal  | rename_principal test@HADOOP.COM test2@ABC.COM |
| get_principal, getprinc                  | 获取 principal   | get_principal test@HADOOP.COM            |
| list_principals, listprincs, get_principals, getprincs | 显示所有 principal | listprincs                               |
| ktadd                                    | 导出条目到 keytab   | ktadd -norandkey -k /opt/rangeradmin.service.keytab rangeradmin/node02 |

kdc-client

| 操作        | 描述       | 示例                                     |
| --------- | -------- | -------------------------------------- |
| kinit     | 密码认证     | kinit root/admin@HADOOP.COM            |
| kinit -kt | keytab认证 | kinit -kt /root/root.keytab root/admin |
| klist     | 当前认证用户   |                                        |
| klist -kt | 查看keytab | klist -kt /root/root.keytab            |
| kdestroy  | 销毁当前认证   |                                        |

