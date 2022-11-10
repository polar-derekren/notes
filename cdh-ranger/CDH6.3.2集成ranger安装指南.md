

[toc]

## CDH6.3.2集成ranger2.1

### 1、概述

**大数据授权管理组件**

- **ranger：** DHP，主流，功能强大，支持列级别管控，行过滤，字段脱敏
- **sentry：**CDH，停止更新

**统一身份认证组件**

- **ldap:** 目录访问协议，一种基于用户名密码的安全认证方式
- **kerberos：** 私密性，防监听，防篡改，安全性，双向认证

**本方案采用ranger+kerberos**

![ranger](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/ranger01.png)

![kerberos](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/kerberos.png)

### 2、前置依赖

官网地址：https://ranger.apache.org

帮助文档：https://cwiki.apache.org/confluence/display/RANGER/Row-level+filtering+and+column-masking+using+Apache+Ranger+policies+in+Apache+Hive

打包后目录：

ranger2.1:71  **/home/airflow/rdg/ranger2.1-cdh6.3.2**

impala3.4:71 **/home/airflow/rdg/impala-3.4**

**admin登录地址**(无kerberos)：<http://192.168.1.71:6080>

**admin登录地址**(集成kerberos)：<http://192.168.1.231:6080> 

**CDH-kerberos管理界面：** http://192.168.1.232:7180/cmf/home

**编译：**

CDH集成版github：https://github.com/gm19900510/ranger/tree/release-ranger-2.1.0-cdh-6.3.1-hylink

**pom.xml中版本改成cdh6.3.2对应组件版本后打包**

kerberos安装

### 3、kerberos安装

- **安装**

```shell
$ yum -y install krb5-server  krb5-workstation krb5-libs#kdc server上安装server
$ yum -y install krb5-devel krb5-workstation #所有kdc client上安装client
```

- **kdc.conf**

kdc server 专属配置服务  **/var/kerberos/krb5kdc/kdc.conf**  

```properties
[kdcdefaults]
 kdc_ports = 88
 kdc_tcp_ports = 88

[realms]
 TAIZHI.COM = {
  #master_key_type = aes256-cts
  acl_file = /var/kerberos/krb5kdc/kadm5.acl
  dict_file = /usr/share/dict/words
  admin_keytab = /var/kerberos/krb5kdc/kadm5.keytab
  supported_enctypes = aes256-cts:normal aes128-cts:normal des3-hmac-sha1:normal arcfour-hmac:normal camellia256-cts:normal camellia128-cts:normal des-hmac-sha1:normal des-cbc-md5:normal des-cbc-crc:normal
  max_life = 25h
  max_renewable_life = 8d 
}

```

- **kadm5.acl**

kdc权限专属服务 **/var/kerberos/krb5kdc/kadm5.acl**

```properties
*/admin@TAIZHI.COM	*
```

- **krb5.conf**

客户端专属配置文件，根据该文件中的信息去访问KDC  **/etc/krb5.conf**

```properties
[logging]
 default = FILE:/var/log/krb5libs.log
 kdc = FILE:/var/log/krb5kdc.log
 admin_server = FILE:/var/log/kadmind.log

[libdefaults]
 dns_lookup_realm = false
 ticket_lifetime = 24h
 renew_lifetime = 7d
 forwardable = true
 rdns = false
 pkinit_anchors = FILE:/etc/pki/tls/certs/ca-bundle.crt
 default_realm = TAIZHI.COM
 # default_ccache_name = KEYRING:persistent:%{uid}

[realms]
 TAIZHI.COM = {
  kdc = node02
  admin_server = node02
 }

[domain_realm]
 .example.com = TAIZHI.COM
 example.com = TAIZHI.COM

```

- 初始化KDC数据库

```properties
kdb5_util create -r TAIZHI.COM -s
```

- 启动kdc server

```shell
#启动服务命令 
systemctl start krb5kdc 
systemctl start kadmin 

#加入开机启动项 
systemctl enable krb5kdc 
systemctl enable kadmin
```

- 创建管理员

```shell
kadmin.local -q "addprinc root/admin"
kadmin.local -q "addprinc cloudera-scm/admin" #使用cloudera-scm/admin作为CM创建其它principals的超级用户
```

### 4、CDH开启kerberos

进入Cloudera Manager的**“管理”->“安全”**界面
**1）选择“启用Kerberos”，进入如下界面**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211129108.png)

**2）环境确认（勾选全部）**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211131706.png)

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211132781.png)

**3）填写KDC配置**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211139431.png)

要注意的是：这里的 Kerberos Encryption Types 必须跟KDC实际支持的加密类型匹配（即kdc.conf中的值）

**4）KRB5 信息**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211141730.png)

**5）填写主体名和密码**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211142952.png)

**6）等待导入KDC凭据完成**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211143438.png)

**7）继续**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211143692.png)

**8）重启集群**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211145091.png)

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211146396.png)

**9）完成**

![](https://raw.githubusercontent.com/Raray-chuan/xichuan_blog_pic/main/img/202210211146415.png)

之后 Cloudera Manager 会自动重启集群服务，启动之后会提示 Kerberos 已启用。

**10）HDFS/YARN/HIVESERVER2开启webui验证**

**1.修改CDH中的配置：**

hdfs 开启 `Enable Kerberos Authentication for HTTP Web-Consoles`

下图中的配置选项选中，使其生效

![image-20221108103411604](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/img/image-20221108103411604.png)

yarn开启 `Enable Kerberos Authentication for HTTP Web-Consoles`

下图中的配置选项选中，使其生效

![image-20221108103447542](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/img/image-20221108103447542.png)

hive设置 `hive.server2.webui.use.spnego=true`

![image-20221108103526595](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/img/image-20221108103526595.png)

**重启CDH**



在 Cloudera Manager 上启用 Kerberos 的过程中，会自动做以下的事情：

集群中有多少个节点，每个账户就会生成对应个数的 principal ;
为每个对应的 principal 创建 keytab；
部署 keytab 文件到指定的节点中；
在每个服务的配置文件中加入有关 Kerberos 的配置；
启用之后访问集群的所有资源都需要使用相应的账号来访问，否则会无法通过 Kerberos 的 authenticatin。

### 5、Ranger-web安装

```shell
tar -zxvf ranger-2.1.0-admin.tar.gz
```

- #### **install.properties**


mysql相关配置信息

如果python不能对应python2版本，修改成对应的python2环境

> ```properties
> PYTHON_COMMAND_INVOKER=python2
>
> #DB_FLAVOR=MYSQL|ORACLE|POSTGRES|MSSQL|SQLA
> DB_FLAVOR=MYSQL
> #
>
> #
> # Location of DB client library (please check the location of the jar file)
> #
> #SQL_CONNECTOR_JAR=/usr/share/java/ojdbc6.jar
> #SQL_CONNECTOR_JAR=/usr/share/java/mysql-connector-java.jar
> #SQL_CONNECTOR_JAR=/usr/share/java/postgresql.jar
> #SQL_CONNECTOR_JAR=/usr/share/java/sqljdbc4.jar
> #SQL_CONNECTOR_JAR=/opt/sqlanywhere17/java/sajdbc4.jar
> SQL_CONNECTOR_JAR=/home/airflow/mid/lib/mysql-connector-java-5.1.45.jar
> db_root_user=root
> db_root_password=xxxx
> db_host=192.168.1.74
> #
> # DB UserId used for the Ranger schema
> #
> db_name=ranger
> db_user=ranger
> db_password=ranger
> audit_store=
> # change password. Password for below mentioned users can be changed only once using this property.
> #PLEASE NOTE :: Password should be minimum 8 characters with min one alphabet and one numeric.
> rangerAdmin_password=Ranger123
> rangerTagsync_password=Ranger123
> rangerUsersync_password=Ranger123
> keyadmin_password=Ranger123
>
> policymgr_external_url=http://airflow-dev:6080
> policymgr_http_enabled=true
> policymgr_https_keystore_file=
> policymgr_https_keystore_keyalias=ranger
> policymgr_https_keystore_password=Ranger123
>
> #Add Supported Components list below separated by semi-colon, default value is empty string to support all components
> #Example :  policymgr_supportedcomponents=hive,hbase,hdfs
> policymgr_supportedcomponents=
>
> #------------ Kerberos Config -----------------
> spnego_principal=HTTP/node02@TAIZHI.COM
> spnego_keytab=/opt/security/keytab/rangeradmin.service.keytab
> token_valid=30
> cookie_domain=
> cookie_path=/
> admin_principal=rangeradmin/node02@TAIZHI.COM
> admin_keytab=/opt/security/keytab/rangeradmin.service.keytab
> lookup_principal=rangeradmin/node02@TAIZHI.COM
> lookup_keytab=/opt/security/keytab/rangeradmin.service.keytab
> hadoop_conf=/etc/hadoop/conf
>
>
> #
> # ------- UNIX User CONFIG ----------------
> #
> unix_user=root
> unix_user_pwd=******
> unix_group=root
>
>
> ```
>
> 

- #### **ranger数据库**


```sql
create database ranger_dev;
CREATE USER 'ranger_dev'@'%' IDENTIFIED BY 'ranger_dev';
GRANT ALL ON ranger_dev.* TO 'ranger_dev'@'%';
```

- #### **setup.sh**


```shell
$ ./setup.sh
```

- #### **ranger-admin-site.xml**


conf/ranger-admin-site.xml

```xml
<property>
      <name>ranger.service.host</name>
      <value>airflow-dev</value>
        </property>

<property>
    <name>ranger.jpa.jdbc.password</name>
    <value>ranger</value>
    <description />
</property>
```

- #### **冲突**


```shell
#删除冲突jar
$ cd /ews/webapp/WEB-INF/lib
$ rm -f javax.ws.rs-api-2.1.jar  jersey-client-2.6.jar jersey-server-2.27.jar
```

- #### **软连接**


如果默认已创建，请忽略

```shell
$ ln -s /usr/bin/ranger-admin /home/airflow/rdg/ranger1.2/target/ranger-1.2.0-admin/ews/ranger-admin-services.sh
$ ln -s /usr/bin/ranger-admin/ranger-usersync/home/airflow/rdg/ranger1.2/target/ranger-1.2.0-usersync/ranger-usersync-services.sh
```

- #### 启用


```shell
$ ranger-admin start
$ ranger-admin stop
```

- #### **Q&A**


如果创建hive-service服务并且可以正常连接hs2服务请忽略此步骤

```txt
HS2连接地址：jdbc:hive2://192.168.1.170:10000/
```

如果无法正常连接，或者协议不对，请按如下操作：

从CDH集群中那对应版本的jar替换admin-web里的jar目录目录

```shell
cp /opt/cloudera/parcels/CDH/jars/hive-* /home/airflow/rdg/ranger1.2/target/ranger-1.2.0-admin/ews/webapp/WEB-INF/classes/ranger-plugins/hive/
```



### 6、ranger用户同步

```shell
tar -zxvf ranger-2.1.0-usersync.tar.gz
```

- #### install.properties


rangerUsersync_password 密码要和admin中的rangerusersync_password保持一致

```properties
#
# The following URL should be the base URL for connecting to the policy manager web application
# For example:
#
#  POLICY_MGR_URL = http://policymanager.xasecure.net:6080
#
POLICY_MGR_URL = http://airflow-dev:6080

# sync source,  only unix and ldap are supported at present
# defaults to unix
SYNC_SOURCE = unix

#
# Minimum Unix User-id to start SYNC.
# This should avoid creating UNIX system-level users in the Policy Manager
#
MIN_UNIX_USER_ID_TO_SYNC = 500

# Minimum Unix Group-id to start SYNC.
# This should avoid creating UNIX system-level users in the Policy Manager
#
MIN_UNIX_GROUP_ID_TO_SYNC = 500

# sync interval in minutes
# user, groups would be synced again at the end of each sync interval
# defaults to 5   if SYNC_SOURCE is unix
# defaults to 360 if SYNC_SOURCE is ldap
SYNC_INTERVAL = 1

#User and group for the usersync process
unix_user=root
unix_group=root

#change password of rangerusersync user. Please note that this password should be as per rangerusersync user in ranger
rangerUsersync_password=Ranger123

#------------ Kerberos Config user-sync不要开启kerberos验证，否则会有issue----------------- 
#https://github.com/apache/ranger/pull/74
usersync_principal=
usersync_keytab=
hadoop_conf=
```

- #### setup.sh


```shell
$./setup.sh
```

- #### ranger-ugsync-site


```xml
#开启同步组件
vim conf/ranger-ugsync-site.xml
<property>
  <name>ranger.usersync.enabled</name>
  <value>true</value>
</property>
```

- #### 启停


```shell
ranger-usersync start
ranger-usersync stop
```

### 7、hive-plugin插件安装

```shell
tar -zxvf ranger-2.1.0-hive-plugin.tar.gz
```

- #### install.properties


```properties
#
# Location of Policy Manager URL  
#
# Example:
# POLICY_MGR_URL=http://policymanager.xasecure.net:6080
#
POLICY_MGR_URL=http://airflow-dev:6080

#
# This is the repository name created within policy manager
#
# Example:
# REPOSITORY_NAME=hivedev
#
REPOSITORY_NAME=hive

#
# Hive installation directory
#
# Example:
# COMPONENT_INSTALL_DIR_NAME=/var/local/apache-hive-2.1.0-bin
#
COMPONENT_INSTALL_DIR_NAME=/opt/cloudera/parcels/CDH/lib/hive
#
# Custom component user
# CUSTOM_COMPONENT_USER=<custom-user>
# keep blank if component user is default
CUSTOM_USER=hive


#
# Custom component group
# CUSTOM_COMPONENT_GROUP=<custom-group>
# keep blank if component group is default
CUSTOM_GROUP=hive

```

- #### xasecure-audit.xml


```shell
#enable目录下添加xml
vim /home/airflow/polar/ranger-1.2.0-hive-plugin/install/conf.templates/enable/xasecure-audit.xml
```

```properties
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.url</name>
  <value>jdbc:mysql://192.168.1.74:3306/ranger</value>
</property>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.user</name>
  <value>ranger</value>
</property>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.password</name>
  <value>ranger</value>
</property>
</configuration>
```

- #### 启用


enable后会往hive/conf下拷贝几个xml文件和hive/lib下创建几个软连接 即表示安装成功

```shell
$ ./enable-hive-plugin.sh
$ ./disable-hive-plugin.sh
```

![1](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/1.png)

![2](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/2.png)



- **ranger-hive-service配置**


```shell
jdbc:hive2://192.168.1.230:10000/;principal=hive/node01@TAIZHI.COM  #hs2中的principal
policy.download.auth.users=hdfs,hive,impala
beeline -u "jdbc:hive2://192.168.1.230:10000/default;principal=hive/node01@TAIZHI.COM"
```

- **取消**

  运行./disable-hive-plugin.sh   

  如果不可取消，请/opt/cloudera/parcels/CDH/lib/hive/conf/hiveserver2-site.xml  里的验证选项改成false即可

- #### Q&A

**如果使用的是CDH适配版本，请忽略此。**

**1.CDH版本存在问题：**

1.hive/lib 下创建的软连接目录无法被加载到classpath

2.重启HS2时hive/conf下的ranger配置文件不会到拷贝到本次HS2运行目录

**解决**

1.将hive/lib/ranger-hive-plugin-impl目录里的ranger包拷贝放入hive/lib下

```shell
cp /opt/cloudera/parcels/CDH/lib/hive/lib/ranger-hive-plugin-impl/* /opt/cloudera/parcels/CDH/lib/hive/lib/
```

2.将ranger-xml拷贝到当前hive工作路径

```shell
cd /opt/cloudera/parcels/CDH/lib/hive/conf
cp hiveserver2-site.xml ranger-hive-audit.xml ranger-hive-security.xml ranger-policymgr-ssl.xml ranger-security.xml xasecure-audit.xml /run/cloudera-scm-agent/process/10101-hive-HIVESERVER2/
```

**2.beeline无法连接HS2**

登录beeline所在机器删除hive-env.sh里的本地校验规则限制

```shell
vim /opt/cloudera/parcels/CDH/lib/hive/conf/hive-env.sh
#删除hive-env.sh第七行export HIVE_OPTS=
```

### 8、hdfs-plugin插件安装

```shell
tar -zxvf ranger-2.1.0-hdfs-plugin.tar.gz
```

- #### install.properties


```properties
#
# Location of Policy Manager URL  
#
# Example:
# POLICY_MGR_URL=http://policymanager.xasecure.net:6080
#
POLICY_MGR_URL=http://airflow-dev:6080

#
# This is the repository name created within policy manager
#
# Example:
# REPOSITORY_NAME=hadoopdev
#
REPOSITORY_NAME=hdfs

#
# Set hadoop home when hadoop program and Ranger HDFS Plugin are not in the
# same path.
#
COMPONENT_INSTALL_DIR_NAME=/opt/cloudera/parcels/CDH/lib/hadoop

#
# Custom component user
# CUSTOM_COMPONENT_USER=<custom-user>
# keep blank if component user is default
CUSTOM_USER=hdfs


#
# Custom component group
# CUSTOM_COMPONENT_GROUP=<custom-group>
# keep blank if component group is default
CUSTOM_GROUP=hadoop
```

- #### xasecure-audit.xml


```shell
cd /home/airflow/polar/ranger-2.1.0-hdfs-plugin/install/conf.templates/enable
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.url</name>
  <value>jdbc:mysql://192.168.1.74:3306/ranger</value>
</property>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.user</name>
  <value>ranger</value>
</property>
<property>
  <name>xasecure.audit.jpa.javax.persistence.jdbc.password</name>
  <value>ranger</value>
</property>
</configuration>
```

- #### 启用


```shell
$ ./enable-hdfs-plugin.sh
$ ./disable-hdfs-plugin.sh
```

- **ranger-hdfs-service配置**


```shell
hdfs://192.168.1.230:8020/;principal=hdfs/node01@TAIZHI.COM #namenode中的principal namenode
```

- #### CDH集群配置


```properties
#hdfs-site.xml 的 HDFS 服务高级配置代码段（安全阀)
dfs.namenode.inode.attributes.provider.class=org.apache.ranger.authorization.hadoop.RangerHdfsAuthorizer
```

- #### Q&A


检查/opt/cloudera/parcels/CDH/lib/hadoop/etc/hadoop 目录下配置文件拷贝正常

检查/opt/cloudera/parcels/CDH/lib/hadoop目录下JAR拷贝正常

**对于CDH集群jar默认是放置再/opt/cloudera/parcels/CDH/lib/hadoop/share/hadoop/hdfs/lib目录下，需要将jar拷贝到/opt/cloudera/parcels/CDH/lib/hadoop 目录下，否则不会生效**

### 9、impala3.2升级3.4

- **编译后的文件位置：be/build/latest/service/，fe/target/**


```shell
#下载源码
git clone --single-branch --branch 3.4.0 https://github.com/apache/impala.git impala-3.4
#修改pom.xml更改maven repo
git fetch origin 481ea4ab0d476a4aa491f99c2a4e376faddc0b03
git cherry-pick 481ea4ab0d476a4aa491f99c2a4e376faddc0b03
#安装编译依赖
cd impala-3.4
export IMPALA_HOME=`pwd`
$IMPALA_HOME/bin/bootstrap_system.sh
#设置环境变量
source $IMPALA_HOME/bin/impala-config.sh
#编译
$IMPALA_HOME/buildall.sh -noclean -notests -release
```

- **升级CDH**


**需要替换的内容：**

```shell
ldd impalad#查看依赖的so
toolchain/kudu-4ed0dbbd1/debug/lib/libkudu_client.so.0
fe/target/dependency/*.jar
ext-data-source/api/target/impala-data-source-api-1.0-SNAPSHOT.jar
fe/target/impala-frontend-0.1-SNAPSHOT.jar
build/latest/service/impalad
www
```

**替换impalad依赖**

```shell
#复制一份新的impala
cp -r impala impala-3.4 
#删除lib下所有jar
rm -f impala-3.4/lib/*.jar 
#libkudu_client.so.0 和libkudu_client.so.0.1.0是同一个文件
cp $IMPALA_HOME/toolchain/kudu-4ed0dbbd1/debug/lib/libkudu_client.so.0  impala-3.4/lib/
cp fe/target/impala-frontend-0.1-SNAPSHOT.jar  impala-3.4/lib/
cp ext-data-source/api/target/impala-data-source-api-1.0-SNAPSHOT.jar impala-3.4/lib/
cp $IMPALA_HOME/fe/target/dependency/*.jar impala-3.4/lib/
cp be/build/latest/service/impalad impala-3.4/sbin-retail/
cp /www/ impala-3.4/www/
```

- **CDH配置**


**Impala 服务环境高级配置代码段（安全阀）：**

**IMPALA_HOME=/opt/cloudera/parcels/CDH/lib/impala-3.4**

### 10、impala-plugin插件安装

**Ranger并未提供impala-plugin插件来适配impala，而是impala主动拥抱ranger的权限管控。**

**impala3.3 版本之后才支持ranger权限管控组件。**



对于CDH版本的impala，需要每次将ranger权限管控文件拷贝到当前impala的启动目录中

- #### impala-plugin配置文件同步


```shell
#从hive conf中将ranger相关配置拷贝到指定目录
/opt/cloudera/parcels/CDH/lib/hive/conf/

#修改impala启动脚本，启动时同步hive权限配置文件
vim /opt/cloudera/cm-agent/service/impala/impala.sh 
if [ "impalad" = "$1" -o "catalogd" = "$1" ]; then
  cp $IMPALA_HOME/*.xml ${CONF_DIR}/hive-conf/
fi

#分发修改后的impala.sh和ranger配置文件到所有机器上(impalad,catalog)
scp -r /opt/cloudera/cm-agent/service/impala/impala.sh root@192.168.1.174:/opt/cloudera/cm-agent/service/impala/
scp -r /opt/cloudera/cm-agent/service/impala/impala.sh root@192.168.1.175:/opt/cloudera/cm-agent/service/impala/
scp -r /opt/cloudera/cm-agent/service/impala/impala.sh root@impala01-dev:/opt/cloudera/cm-agent/service/impala/
scp -r /opt/cloudera/cm-agent/service/impala/impala.sh root@192.168.1.187:/opt/cloudera/cm-agent/service/impala/
scp -r /opt/cloudera/cm-agent/service/impala/impala.sh root@192.168.1.188:/opt/cloudera/cm-agent/service/impal
#重启CDH
```

- #### CDH配置


```shell
#CDH-impala参数中impala daemon命令行参数高级配置，catalog server命令行参数高级配置  分别配置启动参数：
--server-name=hive
--ranger_service_type=hive 
--ranger_app_id=impala
--authorization_provider=ranger
```

```shell
在hiveserver2机器的/etc/ranger/hive目录下可以查看最新的同步策略(hive和impala公用)
```

### 11、web-admin权限配置

**1.HIVE权限管控**

插件安装后会拦截所有经过HS2的JDBC请求

```properties
0: jdbc:hive2://192.168.1.170:10000> select * from test_xac_dws.dws_ft_stdf_chip_id limit 10;
Error: Error while compiling statement: FAILED: HiveAccessControlException Permission denied: user [airflow] does not have [SELECT] privilege on [test_xac_dws/dws_ft_stdf_chip_id/*] (state=42000,code=40000)

```

- ###### **配置访问权限**


##### ![hive-allow](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/hive-allow.png)

- ###### **配置行过滤**


![rowfilter](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/rowfilter.png)

- ###### **配置脱敏**


![mask](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/mask.png)



- ###### 结果


airflow用户只能访问chip_id表 guid=**55e9b624ab7ecc10b4595c791f49917a**的数据行，只能访问除**lot**外的字段，并且对**chip_id**字段脱敏

```properties
0: jdbc:hive2://192.168.1.170:10000> select * from test_xac_dws.dws_ft_stdf_chip_id limit 10;
Error: Error while compiling statement: FAILED: HiveAccessControlException Permission denied: user [airflow] does not have [SELECT] privilege on [test_xac_dws/dws_ft_stdf_chip_id/*] (state=42000,code=40000)
```

```properties
0: jdbc:hive2://192.168.1.170:10000> select distinct guid from test_xac_dws.dws_ft_stdf_chip_id limit 10;
INFO  : Compiling command(queryId=hive_20221018105829_f2ab8992-4b67-4d3d-8c31-35bc4a9fd8bb): select distinct guid from test_xac_dws.dws_ft_stdf_chip_id limit 10
INFO  : Semantic Analysis Completed
INFO  : Returning Hive schema: Schema(fieldSchemas:[FieldSchema(name:guid, type:string, comment:null)], properties:null)
INFO  : OK
+-----------------------------------+
|               guid                |
+-----------------------------------+
| 55e9b624ab7ecc10b4595c791f49917a  |
+-----------------------------------+


```

```properties
0: jdbc:hive2://192.168.1.170:10000> select * from test_xac_dws.dws_ft_stdf_uld_data limit 10;
Error: Error while compiling statement: FAILED: HiveAccessControlException Permission denied: user [airflow] does not have [SELECT] privilege on [test_xac_dws/dws_ft_stdf_uld_data/*] (state=42000,code=40000)

```

![001](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/001.png)

2.HDFS权限管控

- ###### 联合授权模型


**HDFS和ranger采用联合授权方式，只要有一个有权限即获得权限**

**建议execution权限和read,write成套配置，否则可能出现有读写没执行权限等不匹配导致不生效问题。**

对于一个本就有unix系统权限的用户，那么可以配置denny策略，明确拒绝他的read,write,execution策略
如果对一个本没有unix系统权限的用户，可以配置allow策略，允许访问路径的权限

![4](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/4.png)

- ###### 配置拒绝权限


```properties
[airflow@airflow-dev ~]$ hdfs dfs -ls /user/hive/warehouse/test_xac_dws.db/dws_ft_stdf_uld_data_remould
Found 1 items
drwxr-xr-x   - airflow hive          0 2022-08-04 15:55 /user/hive/warehouse/test_xac_dws.db/dws_ft_stdf_uld_data_remould/stat_date=20220804
```

![002](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/002.png)

```properties
[airflow@airflow-dev ~]$ hdfs dfs -ls /user/hive/warehouse/test_xac_dws.db/dws_ft_stdf_uld_data_remould
ls: Permission denied: user=airflow, access=EXECUTE, inode="/user/hive/warehouse/test_xac_dws.db/dws_ft_stdf_uld_data_remould"
```



- ###### 配置允许策略


```properties
##默认没有访问权限
[airflow@airflow-dev ~]$ hdfs dfs -ls /tmp/rangertest
ls: Permission denied: user=airflow, access=READ_EXECUTE, inode="/tmp/rangertest":root:supergroup:drwx------
```

![004](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/004.png)

```properties
#配置后有读权限
[airflow@airflow-dev ~]$ hdfs dfs -ls /tmp/rangertest
Found 1 items
-rw-r--r--   3 airflow supergroup       1210 2022-10-14 09:29 /tmp/rangertest/sound.properties
```

```properties
[airflow@airflow-dev ~]$ hdfs dfs -cat /tmp/rangertest/sound.properties
############################################################
#               Sound Configuration File
############################################################
#
# This properties file is used to specify default service
# providers for javax.sound.midi.MidiSystem and
# javax.sound.sampled.AudioSystem.
#
# The following keys are recognized by MidiSystem methods:
#
# javax.sound.midi.Receiver
# javax.sound.midi.Sequencer
# javax.sound.midi.Synthesizer
# javax.sound.midi.Transmitter
#
# The following keys are recognized by AudioSystem methods:
#
# javax.sound.sampled.Clip
# javax.sound.sampled.Port
# javax.sound.sampled.SourceDataLine
# javax.sound.sampled.TargetDataLine
#
# The values specify the full class name of the service
# provider, or the device name.
#
# See the class descriptions for details.
#
# Example 1:
# Use MyDeviceProvider as default for SourceDataLines:
# javax.sound.sampled.SourceDataLine=com.xyz.MyDeviceProvider
#
# Example 2:
# Specify the default Synthesizer by its name "InternalSynth".
# javax.sound.midi.Synthesizer=#InternalSynth
#
# Example 3:
# Specify the default Receiver by provider and name:
# javax.sound.midi.Receiver=com.sun.media.sound.MidiProvider#SunMIDI1
#

```

**3.IMPALA权限管控**

**impala和hive公用一套权限管控接口**

- 配置visitor部分访问权限

![impala01](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/impala01.png)



- dbeaver只能看到有权限的库和表

![impala03](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/impala03.png)



- 配置roger用户有所有访问权限

![impala02](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/impala02.png)

- roger用户可以访问所有表

![impala04](https://cdn.jsdelivr.net/gh/polar-derekren/tuchuang@main/impala04.png)

### 12、终端连接

- **dbeaver**

安装kfw客户端

```properties
https://web.mit.edu/kerberos/dist/index.html
```

修改krb5.ini

```properties
1.从kdc-server机器上复制/etc/krb5.conf 文件内容
2.替换C:\\ProgramData\\MIT\\Kerberos5\\krb5.ini 文件内容
3.C盘下创建temp目录
4.增加环境变量 KRB5CCNAME=C:\temp\krb5cache
5.增加环境变量  KRB5_CONFIG=C:\ProgramData\MIT\Kerberos5\krb5.ini
6.修改dbeaver.ini
-Djavax.security.auth.useSubjectCredsOnly=false
-Djava.security.krb5.conf=C:\ProgramData\MIT\Kerberos5\krb5.ini
-Dsun.security.krb5.debug=true
dbeaver连接impala
```

dbeaver连接impala

```properties
1.编辑dbaver驱动模板：jdbc:impala://{host}:{port}/{database};AuthMech=1;KrbRealm=TAIZHI.COM;KrbHostFQDN={host};KrbServiceName=impala;KrbAuthType=2
2.参数AuthMech=1;KrbRealm=TAIZHI.COM;KrbHostFQDN=node02;KrbServiceName=impala;KrbAuthType=2
```

dbeaver连接hive

```properties
编辑驱动模板或者添加参数：principal=hive/node01@TAIZHI.COM #hiveserver2所使用的principal
```

- **Windows访问HDFS/YARN/HIVESERVER2 等服务的webui**

​	**1）安装kfw客户端（参考如上）**

​    **2）修改Firefox配置**

由于chrome浏览器中 kerberos 相关配置比较复杂，建议配置使用firefox浏览器。打开firefox浏览器，在地址栏输入`about:config`，然后搜索并配置如下两个参数：
`network.auth.use-sspi`：将值改为`false`；
`network.negotiate-auth.trusted-uris`=node01,node02,node03：将值为集群节点ip或主机名；

- **spark-shell**

```shell
$ spark-shell --executor-memory 1g --executor-cores 1 --num-executors 1 --driver-memory 1G --queue root.default
如提示 user not found 请在linux上创建相应用户
```

- **spark-submit**

```properties
可以本地kinit 认证一个用户（会过期）
或者spark-submit添加
--principal visitor@TAIZHI.COM \
--keytab /opt/visitor.service.keytab \
##提交命令
spark-submit \
--master yarn \
--deploy-mode cluster \
--conf spark.sql.shuffle.partitions=200 \
--principal visitor@TAIZHI.COM \
--keytab /opt/visitor.service.keytab \
--num-executors 1 \
--executor-memory 2G  \
--executor-cores 1 \
--queue root.default \
--class cn.tfinfo.stdf.analyze.mid.SparkKerberos /opt/rdg/tz-ft-analysis.jar nokerberos /etc/krb5.conf /opt/visitor.service.keytab visitor@TAIZHI.COM
```

- **spark-local**

```properties
1.将hadoop集群相关的xml拷贝放到一个目录  core-site.xml，hive-site.xml，mapred-site.xml，yarn-site.xml，hdfs-site.xml
2.在idea project settings中添加XML的存放目录，将其加载的classpath中，让本地集群可以获取
3.代码必须在.doAs作用域内创建spark才有效，否则无法连接hive和hdfs
4.加载了完整的集群配置文件就不需要再创建sparksession时添加hive.metastore.uris参数
```

```scala
def kerberosAuth(krb5ConfPath:String,keytabPath:String,principle:String): UserGroupInformation ={
    val conf = new Configuration
    System.setProperty("java.security.krb5.conf", krb5ConfPath)
    UserGroupInformation.setConfiguration(conf)
    val loginInfo = UserGroupInformation.loginUserFromKeytabAndReturnUGI(principle, keytabPath)

    if (loginInfo.hasKerberosCredentials) {
      println("kerberos authentication success!")
      println("login user: "+loginInfo.getUserName())
      return loginInfo
    } else {
      println("kerberos authentication fail!")
      return null
    }
    
    
    def main(args: Array[String]): Unit = {
    val flag=args(0)
    if(flag.equals("kerberos"))
    {
      println("kerberos auth!!")
      val loginInfo = kerberosAuth("E:\\kerberos\\krb5.conf","E:\\kerberos\\roger.service.keytab","roger/admin@TAIZHI.COM")
      loginInfo.doAs(new PrivilegedAction[Unit] (){
        override def run(): Unit = {
          val spark = SparkEngine.getSparkSession(true)
          spark.sql("select count(*) from test_xac_dws.dws_ft_stdf_chip_id where guid='LX-2021122'").show()
          spark.read.textFile("hdfs://node01/user/hive/warehouse/test_xac_dws.db/kerberos/abc.txt").show()
        }
      })
    }else{
      val spark = SparkEngine.getSparkSession(true)
      spark.sql("select count(*) from test_xac_dws.dws_ft_stdf_chip_id where guid='LX-2021122'").show()
      spark.read.textFile("hdfs://node01/user/hive/warehouse/test_xac_dws.db/kerberos/abc.txt").show()
    }
  }
```

- **flink-session**

```shell
$ vim flink-conf.yaml
security.kerberos.login.use-ticket-cache: true
security.kerberos.login.keytab: /opt/visitor.service.keytab
security.kerberos.login.principal: visitor@TAIZHI.COM
security.kerberos.login.contexts: Client
```

```scala
#从kafka接收数据写入到hdfs中，同时受kerberos+ranger权限管控
val sink: StreamingFileSink[String] = StreamingFileSink
      .forRowFormat(new Path("hdfs://node01/user/hive/warehouse/test_xac_dws.db/kerberos.test"), new SimpleStringEncoder[String]("UTF-8"))
      .withRollingPolicy(
        DefaultRollingPolicy.builder()
          .withRolloverInterval(TimeUnit.SECONDS.toMillis(15))
          .withInactivityInterval(TimeUnit.SECONDS.toMillis(5))
          .withMaxPartSize(1024 * 1024 * 1024)
          .build())
      .build()
    sinkstream.addSink(sink)
```

```shell
[root@node02 conf]# hdfs dfs -ls /user/hive/warehouse/test_xac_dws.db
Found 2 items
drwxrwxrwt   - root    hive          0 2022-10-26 09:32 /user/hive/warehouse/test_xac_dws.db/dws_ft_stdf_chip_id
drwxrwx---   - visitor hive          0 2022-11-04 15:23 /user/hive/warehouse/test_xac_dws.db/kerberos.test
```

- **Java jdbc**

```java
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
/**
 * @Author Xichuan
 * @Date 2022/10/28 17:53
 * @Description
 */
public class TestKerberosImpala {
        public static final String KRB5_CONF = "E:\\kerberos\\krb5.conf";
        public static final String PRINCIPAL = "roger/admin@TAIZHI.COM";
        public static final String KEYTAB = "E:\\kerberos\\roger.service.keytab";
        public static String connectionUrl = "jdbc:impala://node01:21050/;AuthMech=1;KrbRealm=TAIZHI.COM;KrbHostFQDN=node01;KrbServiceName=impala";
        public static String jdbcDriverName = "com.cloudera.impala.jdbc41.Driver";
    public static String hive_connectionUrl = "jdbc:hive2://192.168.1.230:10000/default;principal=hive/node01@TAIZHI.COM";
    public static String hive_jdbcDriverName = "org.apache.hive.jdbc.HiveDriver";

        public static void main(String[] args) throws Exception {
            int a =kerberosAuth(KRB5_CONF,KEYTAB,PRINCIPAL).doAs(new PrivilegedAction<Integer>(){
            @Override
            public Integer run() {
                return runaction();
            }
        	});
        }

    public static  int runaction(){
        int result1 = 0;
        try {
            Class.forName(jdbcDriverName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try (Connection con = DriverManager.getConnection(connectionUrl)) {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT count(1) FROM test_xac_dws.dws_ft_stdf_chip_id where guid='LX-2021122'");
            while (rs.next()) {
                result1 = rs.getInt(1);
            }
            stmt.close();
            con.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result1;
    }
    
    /**
     * kerberos authentication
     * @param krb5ConfPath
     * @param keyTabPath
     * @param principle
     * @return
     * @throws IOException
     */
        public static UserGroupInformation kerberosAuth(String krb5ConfPath, String keyTabPath, String principle) throws IOException {
            System.setProperty("java.security.krb5.conf", krb5ConfPath);
            Configuration conf = new Configuration();
            conf.set("hadoop.security.authentication", "Kerberos");
            UserGroupInformation.setConfiguration(conf);
            UserGroupInformation loginInfo = UserGroupInformation.loginUserFromKeytabAndReturnUGI(principle, keyTabPath);


            if (loginInfo.hasKerberosCredentials()) {
                System.out.println("kerberos authentication success!");
                System.out.println("login user: "+loginInfo.getUserName());
            } else {
                System.out.println("kerberos authentication fail!");
            }

            return loginInfo;
        }
}

#hive 依赖的maven
<dependency>
            <groupId>org.apache.hive</groupId>
            <artifactId>hive-jdbc</artifactId>
            <version>2.1.1-cdh6.3.2</version>
            <exclusions>
                <exclusion>
                    <groupId>org.apache.hive</groupId>
                    <artifactId>hive-llap-server</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    
```

- **springboot connection pool** 

参考：https://github.com/Raray-chuan/springboot-kerberos-hikari-impala

impala-3.4 新特性：

https://issues.apache.org/jira/browse/IMPALA-7957
https://issues.apache.org/jira/browse/IMPALA-7784
https://issues.apache.org/jira/browse/IMPALA-8891
https://issues.apache.org/jira/browse/IMPALA-8386