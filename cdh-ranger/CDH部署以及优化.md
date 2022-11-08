# **Cloudera_Manager_6.3.2**安装配置文档

## 版本记录

| 版本号 | 时间     | 记录人 | 变更原因 | 变更描述 |
| ------ | -------- | ------ | -------- | -------- |
| 1.0    | 20210522 | 王广涛 | 新建     | 新建     |
| 2.0    | 20210823 | 王广涛 | 增加     | 增加     |
|        |          |        |          |          |
|        |          |        |          |          |

## 1.  配置准备

Cloudera Manager (简称CM)用于管理CDH6集群，可进行节点安装、配置、服务配置等，提供Web窗口界面提高了Hadoop配置可见度，而且降低了集群参数设置的复杂度。

本次CM安装配置规划如下：

| CM安装配置规划               |                                                              |
| ---------------------------- | ------------------------------------------------------------ |
| 机器                         | 192.168.1.170 master01-dev  192.168.1.171 master02-dev  192.168.1.172 master03-dev  192.168.1.173 data01-dev  192.168.1.174 data02-dev  192.168.1.175 data03-dev  192.168.1.176 process01-dev  192.168.1.177 process02-dev  192.168.1.178 process03-dev  192.168.1.179 es01-dev  192.168.1.180 es02-dev  192.168.1.181 es03-dev  192.168.1.182 app01-dev  192.168.1.183 app02-dev  192.168.1.184 app03-dev |
| 系统                         | Centos7.6                                                    |
| 系统内核                     | Linux data01-dev  3.10.0-957.el7.x86_64 #1 SMP Thu Nov 8 23:39:32 UTC 2018 x86_64 x86_64 x86_64  GNU/Linux |
| JDK                          | 1.8.0_231                                                    |
| Ntpd服务                     | server 192.168.1.241                                         |
| cloudera-scm-server节点      | 192.168.1.172                                                |
| cloudera-scm-agent节点       | 192.168.1.[170-184]                                          |
| cloudera安装包公司内网服务器 | http://192.168.1.241/cdh/6.3.2                               |
| Parcel包文件                 | CDH-6.3.2-1.cdh6.3.2.p0.1605554-el7.parcel                   |
| Parcel包下载地址             | http://192.168.1.241/cdh/6.3.2                               |

### 1.1 新建集群http服务配置

配置http服务用于安装CM之后进行parcel的分发

``` shell
1.安装httpd服务
yum install httpd -y
2.重启httpd服务
systemctl start httpd.service
3.配置自启动
systemctl enable httpd.service
4.测试
拷贝cdh相关文件到到/var/www/html 目录
```

## 2.  系统安装

CM安装统一使用root用户安装，通过Xshell工具使用root用户登录X台机器。

### 2.1 文件同步脚本 hosts_sync.sh

 ```shell
#!/bin/bash

# Useage : You shuld install expect to use this script ,for command [rpm -ivh tcl-8.5.13-8.el7.x86_64.rpm]  [rpm -ivh expect-5.45-14.el7_1.x86_64.rpm] 
#
# Reference : sh hosts_sync.sh  		[hosts_file_path] [the same passwd] [Synchronized_file] [-Target_path]
# 				 hosts_file_path 		: proposal value is '/etc/hosts'
#				 the same passwd  		: The same password for each machine
#				 synchronized_file		: proposal value is '/home/documents/file.txt'
#    			 -target_path(Optional) : proposal value is '/home/targetDir'

rpm -q expect &>/dev/null
if [ $? -ne 0 ];then
    yum install -y expect >/dev/null
    if [ $? -eq 0 ]; then
	 echo "expect install success!"
    else
	 echo "expect install failure!"
	 exit;
    fi
fi

# 判断是否有参数
if [ $# -lt 3 ]; then
    echo Not enough Arguement!
    exit;

elif [ ! -e $1 ] || [ ! -e $3 ]; then
    echo Flie Not Exist!
    exit;
fi

#从文件读取主机ip地址
n=0
for host in `awk '{print $1}' $1`
do  
    ((n++));
    if [ $n -gt 2 ]; then 
	count=0
	while [ $count -le 3 ]
	do
    	    ping -c1 -w5 $host >/dev/null 2>&1
    	    if [ $? -eq 0 ]; then
           	#获取文件父目录
		pdir=$(cd -P $(dirname $3); pwd)
		if [ x"$4" = x ]; then
	        targetDir=$pdir
		else
		    targetDir=$4
		fi
     	        #获取当前文件名称
    	        fname=$(basename $3)

	        #在主机上创建目录并进行同步
      		expect -c "
		spawn ssh  $host
		expect {
			\"yes/no\" {send \"yes\r\"; exp_continue}
			\"*assword\" {send \"$2\r\"; exp_continue}
			\"root@*\" {send \"test -d $pdir || mkdir -p $pdir\r exit\r\"; exp_continue} 
			}
		spawn scp -r $pdir/$fname $host:$targetDir
		expect {
                        \"*assword\" { send \"$2\r\"; exp_continue}
                       }"
	    	 #判断文件是否同步成功		
	    	 if [ $? -ne 0 ]; then
				echo "$host sync failure!"
	    	  fi
		break	 
    	    else
		((count++)); 
    	    fi
	done
		if [ $count -gt 3 ]; then
           echo "$host ping is failure!"
        fi
    fi
done

 ```

#### 2.1.1 同步列表  ip.txt

```shell
192.168.1.170
192.168.1.171
192.168.1.172
192.168.1.173
192.168.1.174
192.168.1.175
192.168.1.176
192.168.1.177
192.168.1.178
192.168.1.179
192.168.1.180
192.168.1.181
192.168.1.182
192.168.1.183
192.168.1.184
```

#### 2.1.2 同步脚本 参数2 统一密码

#### 2.1.3 同步脚本 参数3 需要同步的文件

#### 2.1.4 同步脚本 参数4 [可选] 为空时默认为同步文件父目录，不为空时为指定目录

```shell
sh hosts_sync.sh ip.txt 统一密码  同步文件  [同步目的端目录]
```

### 2.2 全局安装JDK1.8+

JDK1.8+是CM依赖的关键，所以每台机器都必须安装全局的JDK1.8+，并配置环境变量。

#### 2.2.1 输入命令 rpm -qa|grep java 查看当前系统是否安装过JDK

![image-20210823173537477](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173537477.png)                               

#### 2.2.2 输入命令 echo $PATH 查看环境变量中是否存在JDK安装的路径

 ![image-20210823173604749](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173604749.png)

#### 2.2.3 如果系统中存在JDK并且和当前版本要求一样，同时PATH中有JDK安装路径，那就不需要再安装JDK，即下面步骤直接跳过，否则需要将当前JDK卸载掉，重新安装自己需要的JDK。

输入命令 rpm -e --nodeps [jdk软件包名称]      （软件名称就是第一步查找到的软件名称）

 ![image-20210823173618778](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173618778.png)

#### 2.2.4 将jdk-8u231-linux-x64.rpm安装包上传至/opt目录下.

#### 2.2.5  输入命令 rpm -ivh jdk-8u231-linux-x64.rpm 解压安装包

```shell
rpm -ivh jdk-8u231-linux-x64.rpm
```

 ![image-20210823173638456](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173638456.png)

#### 2.2.6 rpm安装模式会将jdk安装到/usr/java/目录下。

 ![image-20210823173703115](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173703115.png)

#### 2.2.7  输入命令 vim /etc/profile  修改全局环境变量

```shell
export JAVA_HOME=/usr/java/jdk1.8.0_231-amd64
export PATH=$PATH:$JAVA_HOME/bin
```

 ![image-20210823173722845](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173722845.png)

#### 2.2.8  输入命令 source /etc/profile 使环境变量生效

 ```shell
source /etc/profile
 ```

#### 2.2.9  输入命令 echo $PATH 查看环境变量，输入命令java –version 查看jdk版本

![image-20210823173824742](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823173824742.png)

####  2.2.10  同步rpm包到其他机器

```shell
sh hosts_sync.sh ip.txt 统一密码  /opt/jdk-8u231-linux-x64.rpm
```

### 2.3 配置root密码统一

为了保证CM正常简单安装，将需要安装的几台机器的root密码设置相同，本文中使用的四台机器的root密码一致，都为“pwd”，如果密码不相同，可以执行命令 passwd 进行修改，修改成功后重启系统。

### 2.4 配置hostname和DNS静态域名（使用同步脚本同步到所有机器）

#### 2.4.1 给集群所有机器配置hosts。命令:vim /etc/hosts

```
注意：hostname中不要出现特殊字符例如 _(下划线)
```

#### 2.4.2 输入命令 vi /etc/hosts 配置DNS静态域名，在hosts文件的尾部添加如下内容：

 ```shell
192.168.1.170 master01-dev
192.168.1.171 master02-dev
192.168.1.172 master03-dev
192.168.1.173 data01-dev
192.168.1.174 data02-dev
192.168.1.175 data03-dev
192.168.1.176 process01-dev
192.168.1.177 process02-dev
192.168.1.178 process03-dev
192.168.1.179 es01-dev
192.168.1.180 es02-dev
192.168.1.181 es03-dev
192.168.1.182 app01-dev
192.168.1.183 app02-dev
192.168.1.184 app03-dev
 ```

#### 2.4.3  同步hosts

```shell
sh hosts_sync.sh ip.txt 统一密码  /etc/hosts
```

### 2.5 关闭selinux及防火墙（使用同步脚本同步到所有机器）

在CM安装中每台机器都要关闭selinux和防火墙，所以下面操作在X台机器上都要操作一遍

#### 2.5.1  输入命令 vi /etc/selinux/config 修改config文件中的 SELINUX="" 为 disabled ，关闭selinux，永久生效

![image-20210823180013973](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823180013973.png)

#### 2.5.2 查看命令 /usr/sbin/sestatus -v 

#### 2.5.3  临时关闭  setenforce 0 

#### 2.5.4 关闭防火墙systemctl stop firewalld

#### 2.5.5 取消防火墙开机自启systemctl disable firewalld

#### 2.5.6 service firewalld status 查看防火墙状态 

![image-20210823180040098](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823180040098.png)

```shell
1   
	vi /etc/selinux/config
	SELINUX=disabled
2
 	/usr/sbin/sestatus -v 
3
 	setenforce 0
4
	systemctl stop firewalld
5
	systemctl disable firewalld
6
	service firewalld status
```

#### 2.5.7 同步脚本

```shell
sh hosts_sync.sh ip.txt 统一密码  /etc/selinux/config
```

### 2.6 配置ssh免密码认证 master节点

#### 2.6.1  在主节点上生成公钥

```shell
ssh-keygen -t rsa
```

 ![image-20210823180114376](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823180114376.png)

#### 2.6.2  将公钥拷贝到agent节点 同步脚本同步

 ```shell
cp /root/.ssh/id_rsa.pub /root/.ssh/authorized_keys
sh hosts_sync.sh ip.txt 统一密码  /root/.ssh/authorized_keys
 ```

#### 2.6.3  验证是否完成ssh免密登录(每台机器都要验证一次)

###  2.7 安装mysql数据库

#### 2.7.1 安装mariadb

```shell
yum install mariadb-server
```

#### 2.7.2 配置mariadb

```shell
systemctl start mariadb  # 开启服务
systemctl enable mariadb  # 设置为开机自启动服务
```

#### 2.7.3 初始化

```shell
whereis mysql_secure_installation
/usr/bin/mysql_secure_installation
```

![image-20210823184103258](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823184103258.png)

 

#### 2.7.4 配置数据库

```shell
mysql -uroot –proot
mysql> show databases; #显示数据库
mysql> use mysql; #使用MySQL数据库
mysql> select User, Host from user;
mysql> update user set Host='%' where User='root'; #允许远程登陆
mysql> flush privileges; #刷新权限
mysql> create database hive DEFAULT CHARSET utf8 COLLATE utf8_general_ci; # hive元数据库
mysql> create database amon DEFAULT CHARSET utf8 COLLATE utf8_general_ci; # monitor元数据库
mysql> grant all on hive.* to root@"%" Identified by "root"; #授权hive库中所有表给root用户
mysql> grant all on amon.* to root@"%" Identified by "root"; #授权monitor库中所有表给root用户
mysql> flush privileges; 
mysql> quit; #刷新
```

###  2.8 配置ntp时间同步

#### 2.8.1 安装ntp服务

```shell
yum install -y ntp
```

#### 2.8.2 配置ntp服务

```shell
service ntpd start   # 开启服务
chkconfig ntpd on  # 设置为开机自启动服务
```

#### 2.8.3 配置ntp.conf  vim /etc/ntp.conf 服务端

```properties
# For more information about this file, see the man pages
# ntp.conf(5), ntp_acc(5), ntp_auth(5), ntp_clock(5), ntp_misc(5), ntp_mon(5).

driftfile /var/lib/ntp/drift

# Permit time synchronization with our time source, but do not
# permit the source to query or modify the service on this system.
restrict default nomodify notrap nopeer noquery

# Permit all access over the loopback interface.  This could
# be tightened as well, but to do so would effect some of
# the administrative functions.
restrict 127.0.0.1 
restrict ::1

# Hosts on local network are less restricted.
#restrict 192.168.1.0 mask 255.255.255.0 nomodify notrap

# Use public servers from the pool.ntp.org project.
# Please consider joining the pool (http://www.pool.ntp.org/join.html).
#server 0.centos.pool.ntp.org iburst
#server 1.centos.pool.ntp.org iburst
#server 2.centos.pool.ntp.org iburst
#server 3.centos.pool.ntp.org iburst
server 192.168.1.241
#broadcast 192.168.1.255 autokey	# broadcast server
#broadcastclient			# broadcast client
#broadcast 224.0.1.1 autokey		# multicast server
#multicastclient 224.0.1.1		# multicast client
#manycastserver 239.255.254.254		# manycast server
#manycastclient 239.255.254.254 autokey # manycast client

# Enable public key cryptography.
#crypto

includefile /etc/ntp/crypto/pw

# Key file containing the keys and key identifiers used when operating
# with symmetric key cryptography. 
keys /etc/ntp/keys

# Specify the key identifiers which are trusted.
#trustedkey 4 8 42

# Specify the key identifier to use with the ntpdc utility.
#requestkey 8

# Specify the key identifier to use with the ntpq utility.
#controlkey 8

# Enable writing of statistics records.
#statistics clockstats cryptostats loopstats peerstats

# Disable the monitoring facility to prevent amplification attacks using ntpdc
# monlist command when default restrict does not include the noquery flag. See
# CVE-2013-5211 for more details.
# Note: Monitoring will not be disabled with the limited restriction flag.
disable monitor

```

#### 2.8.4 配置ntp.conf  vim /etc/ntp.conf 客户端

```properties
# For more information about this file, see the man pages
# ntp.conf(5), ntp_acc(5), ntp_auth(5), ntp_clock(5), ntp_misc(5), ntp_mon(5).

driftfile /var/lib/ntp/drift

# Permit time synchronization with our time source, but do not
# permit the source to query or modify the service on this system.
restrict default nomodify notrap nopeer noquery

# Permit all access over the loopback interface.  This could
# be tightened as well, but to do so would effect some of
# the administrative functions.
restrict 127.0.0.1 
restrict ::1

# Hosts on local network are less restricted.
#restrict 192.168.1.0 mask 255.255.255.0 nomodify notrap

# Use public servers from the pool.ntp.org project.
# Please consider joining the pool (http://www.pool.ntp.org/join.html).
#server 0.centos.pool.ntp.org iburst
#server 1.centos.pool.ntp.org iburst
#server 2.centos.pool.ntp.org iburst
#server 3.centos.pool.ntp.org iburst
server 192.168.1.241 #配置ntpserver服务
#broadcast 192.168.1.255 autokey	# broadcast server
#broadcastclient			# broadcast client
#broadcast 224.0.1.1 autokey		# multicast server
#multicastclient 224.0.1.1		# multicast client
#manycastserver 239.255.254.254		# manycast server
#manycastclient 239.255.254.254 autokey # manycast client

# Enable public key cryptography.
#crypto

includefile /etc/ntp/crypto/pw

# Key file containing the keys and key identifiers used when operating
# with symmetric key cryptography. 
keys /etc/ntp/keys

# Specify the key identifiers which are trusted.
#trustedkey 4 8 42

# Specify the key identifier to use with the ntpdc utility.
#requestkey 8

# Specify the key identifier to use with the ntpq utility.
#controlkey 8

# Enable writing of statistics records.
#statistics clockstats cryptostats loopstats peerstats

# Disable the monitoring facility to prevent amplification attacks using ntpdc
# monlist command when default restrict does not include the noquery flag. See
# CVE-2013-5211 for more details.
# Note: Monitoring will not be disabled with the limited restriction flag.
disable monitor
```

#### 2.8.5 分发客户端配置

```shell
sh hosts_sync.sh ip.txt 统一密码  /etc/ntp.conf
```



### 2.9 配置repo

####  2.9.1 CDH.repo

```properties
[CDH-5.8.0]
name=CDH Version - CDH-5.8.0
baseurl=http://hadoop01/CDH/5.8.0/ #1.1 http服务的ip
gpgcheck=0
gpgkey=http://hadoop01/CDH/5.8.0/RPM-GPG-KEY-cloudera
enabled=1
priority=1
```

#### 2.9.2 分发CDH.repo

```shell
sh hosts_sync.sh ip.txt 统一密码  /etc/yum.repos.d/CDH.repo
```

#### 2.9.3 重新加载yum源

```shell
yum clean all #清楚当前yum缓存
yum  update  #更新yum
yum makecache #缓存当前配置yum
```



## 3 安装cloudera

### 3.1 安装CM server ……

```shell
yum install cloudera* #从配置的CDH.repo中通过yum自动安装rpm包(server deamons ……)
```

### 3.2 配置mysql驱动到共享目录

```shell
cp /opt/mysql-connector-java-5.1.47.jar /opt/mysql-connector-java.jar
cp /mysql-connector-java.jar /usr/share/java/
cp /mysql-connector-java.jar /opt/cloudera/cm/lib/
```

### 3.3 创建CM server数据库

```shell
/opt/cloudera/cm/schema/scm_prepare_database.sh -h mysqlIP -P 3306 mysql scm scm scm
# 参数说明
# -h：Database host
# --scm-host：SCM server's hostname
```

![image-20210823190623543](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190623543.png)

### 3.4 启动服务服务端

```shell
service cloudera-scm-server start #启动server
systemctl enable cloudera-scm-server #配置自启动
netstat -anp | grep 7180 #观察server web端口是否被占用(server web服务是否启动)
```

### 3.5 登陆CM管理页面，默认账号/密码  admin/admin

![image-20210823190858697](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190858697.png)

 

###  3.6 配置第三方服务包注册jar

```shell
cd /opt/cloudera/csd #进入server端的csd目录
wget http://192.168.1.241/cdh/es/ELASTICSEARCH-1.0.jar #从http中下载封装完成的ES注册包
service cloudera-scm-server restart #重启server服务
```



## 4 集群安装

1、 进入CM管理界面之后

 ![image-20210823190921135](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190921135.png)

 

2、 选择免费版本的CDH 继续

 ![image-20210823190928452](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190928452.png)

3、 CDH相关介绍页面

 ![image-20210823190938224](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190938224.png)

 

4、 CDH主机获取

 ![image-20210823190947360](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190947360.png)

5、 集群安装parcels选择 删除多余parcel远程库连接后保存

 ![image-20210823190957321](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823190957321.png)

![image-20210823191037220](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191037220.png)

![image-20210823191049565](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191049565.png)

6、 继上一步点击继续  分发agent

  ![image-20210823191058355](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191058355.png)

7、 继上一步点击继续  开始安装选定parcel 

 ![image-20210823191105846](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191105846.png)

等待其安装完成  点击继续进入主机正确性验证

 ![image-20210823191113276](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191113276.png)

进入主机正确性验证 这里会有两个告警

 ![image-20210823191120124](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191120124.png)

```
警告1：Cloudera 建议将 /proc/sys/vm/swappiness 设置为 10。当前设置为 30。使用 sysctl 命令在运行时更改该设置并编辑 /etc/sysctl.conf 以在重启后保存该设置。您可以继续进行安装，但可能会遇到问题，Cloudera Manager 报告您的主机由于交换运行状况不佳。以下主机受到影响： 

通过echo 10 > /proc/sys/vm/swappiness即可解决。（所有主机）

警告2：已启用透明大页面压缩，可能会导致重大性能问题。

请运行（所有主机）

echo never > /sys/kernel/mm/transparent_hugepage/defrag和

echo never > /sys/kernel/mm/transparent_hugepage/enabled以禁用此设置，然后将同一命令添加到 /etc/rc.local 等初始脚本中，以便在系统重启时予以设置。

 

重新进行主机正确性验证：告警消失，点击完成进入组件安装阶段。
```



8、 组件安装 ALL

 ![image-20210823191204216](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191204216.png)

给组件分配角色：根据集群资源进行相关配置  ZK需要三台做协同，其他组件都可以使用默认配置。

![image-20210823191216555](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191216555.png)

填写连接信息并且进行连通性测试

 ![image-20210823191220790](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191220790.png)

集群设置：选择默认即可

 ![image-20210823191226769](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210823191226769.png)

 

安装完毕

 

## 5 集群优化

### 5.1 kafka ip访问配置

```properties
listeners=PLAINTEXT://0.0.0.0:9092,
advertised.listeners=PLAINTEXT://192.168.1.179:9092
##每个broker配置   配置key：kafka.properties 的 Kafka Broker 高级配置代码段（安全阀）  
```

### 5.2 kafka jmx访问

```properties
-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:G1HeapRegionSize=16M -XX:MinMetaspaceFreeRatio=50 -XX:MaxMetaspaceFreeRatio=80 -XX:+DisableExplicitGC -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dcom.sun.management.jmxremote.host=es01-dev -Dcom.sun.management.jmxremote.local.only=true -Djava.rmi.server.hostname=es01-dev
##每个broker中对   配置key：Additional Broker Java Options进行新增配置  
```

### 5.3 hdfs数据目录配置

```properties
/home/data/dfs/dn
##hdfs启用HA之前进行配置更改  配置key：DataNode 数据目录 dfs.datanode.data.dir
```

### 5.4 hdfs纠删码服务配置

```properties
No Default Erasure Coding Policy.
##勾选以上配置 配置key：Fallback Erasure Coding Policy dfs.namenode.ec.system.default.policy
```

### 5.5 spark log4j日志级别配置

```properties
log4j.logger.cn.tfinfo=INFO, tzapp
log4j.additivity.cn.tfinfo=false
log4j.appender.tzapp=org.apache.log4j.ConsoleAppender
log4j.appender.tzapp.target=System.out
log4j.appender.tzapp.layout=org.apache.log4j.PatternLayout
log4j.appender.tzapp.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %p %c{1}: [BIGDATA] %m%n
log4j.appender.tzapp.Encoding=UTF-8
### 配置key：Gateway 日志记录高级配置代码段（安全阀）
```

### 5.6 spark dynamic取消配置

```properties
取消勾选
### 配置key：Enable Dynamic Allocation spark.dynamicAllocation.enabled
```



### 5.7 impala 负载均衡配置 

```properties
1.寻找一台主机 
yum install haproxy -y
2.vim /etc/haproxy/haproxy.cfg
#---------------------------------------------------------------------
# Example configuration for a possible web application.  See the
# full configuration options online.
#
#   http://haproxy.1wt.eu/download/1.4/doc/configuration.txt
#
#---------------------------------------------------------------------

#---------------------------------------------------------------------
# Global settings
#---------------------------------------------------------------------
global
    # to have these messages end up in /var/log/haproxy.log you will
    # need to:
    #
    # 1) configure syslog to accept network log events.  This is done
    #    by adding the '-r' option to the SYSLOGD_OPTIONS in
    #    /etc/sysconfig/syslog
    #
    # 2) configure local2 events to go to the /var/log/haproxy.log
    #   file. A line like the following can be added to
    #   /etc/sysconfig/syslog
    #
    #    local2.*                       /var/log/haproxy.log
    #
    log         127.0.0.1 local2

    chroot      /var/lib/haproxy
    pidfile     /var/run/haproxy.pid
    maxconn     4000
    user        haproxy
    group       haproxy
    daemon

    # turn on stats unix socket
    stats socket /var/lib/haproxy/stats

#---------------------------------------------------------------------
# common defaults that all the 'listen' and 'backend' sections will
# use if not designated in their block
#---------------------------------------------------------------------
defaults
    mode                    http
    log                     global
    option                  httplog
    option                  dontlognull
    option http-server-close
    #option forwardfor       except 127.0.0.0/8
    option                  redispatch
    retries                 3
    timeout http-request    10s
    timeout queue           1m
    timeout connect         10s
    timeout client          1m
    timeout server          1m
    timeout http-keep-alive 10s
    timeout check           10s
    maxconn                 3000

#---------------------------------------------------------------------
# main frontend which proxys to the backends
#---------------------------------------------------------------------
frontend  main *:5000
    acl url_static       path_beg       -i /static /images /javascript /stylesheets
    acl url_static       path_end       -i .jpg .gif .png .css .js

    use_backend static          if url_static
    default_backend             app

#---------------------------------------------------------------------
# static backend for serving up images, stylesheets and such
#---------------------------------------------------------------------
backend static
    balance     roundrobin
    server      static 127.0.0.1:4331 check

#---------------------------------------------------------------------
# round robin balancing between the various backends
#---------------------------------------------------------------------
backend app
    balance     roundrobin
    server  app1 127.0.0.1:5001 check
    server  app2 127.0.0.1:5002 check
    server  app3 127.0.0.1:5003 check
    server  app4 127.0.0.1:5004 check
## 配置haproxy监听impala shell
listen impala_shell data01-dev:25003
    mode tcp
    option tcplog
    balance leastconn
    #主机列表
    server impala1 data01-dev:21000
    server impala2 data02-dev:21000
    server impala3 data03-dev:21000
## 配置haproxy监听impala jdbc
listen impala_jdbc data01-dev:25004
    mode tcp
    option tcplog
    balance leastconn
    #主机列表
    server impala1 data01-dev:21050
    server impala2 data02-dev:21050
    server impala3 data03-dev:21050
3.配置haproxy服务
systemctl start haproxy #启动服务
systemctl enable haproxy #配置开机自启
```

### 5.8  impala自动刷新hive元数据

```properties
名称：hive.metastore.dml.events
值：true
##配置key1： hive-site.xml 的 Hive 客户端高级配置代码段（安全阀）
##配置key2： hive-site.xml 的 Hive 服务高级配置代码段（安全阀）

名称：hive.metastore.notifications.add.thrift.objects
值：true
名称：hive.metastore.alter.notifications.basic
值：false
##配置key： hive-site.xml 的 Hive Metastore Server 高级配置代码段（安全阀）
```

### 5.9 impala调整时区

```properties
-use_local_tz_for_unix_timestamp_conversions=true
##配置key:	Impala Daemon 命令行参数高级配置代码段（安全阀）
```



### 5.10 monitor日志存储目录 根据机器磁盘进行规划存储目录

```properties
/var/lib/cloudera-scm-eventserver
##配置key：	Event Server 索引目录
/var/lib/cloudera-host-monitor
##配置key：	Host Monitor 存储目录 firehose.storage.base.directory
/var/lib/cloudera-service-monitor
##配置key：	Service Monitor 存储目录 firehose.storage.base.directory
/var/log/cloudera-scm-firehose
##配置key：	Activity Monitor 日志目录Activity Monitor Default Group 
/var/log/cloudera-scm-alertpublisher
##配置key：	Alert Publisher 日志目录 Alert Publisher Default Group 
/var/log/cloudera-scm-eventserver
##配置key：	Event Server 日志目录 Event Server Default Group    
/var/log/cloudera-scm-firehose
##配置key：	Host Monitor 日志目录 Host Monitor Default Group 	   
/var/log/cloudera-scm-firehose
##配置key:	Service Monitor 日志目录 Service Monitor Default Group 
```

### 5.11 hdfs namenode审计功能关闭

```properties
log4j.logger.org.apache.hadoop.hdfs.server.namenode.FSNamesystem.audit=WARN
##配置key：	NameNode 日志记录高级配置代码段（安全阀）
```

### 5.12 hdfs开启HA 且hive启用连接改为HA模式

```properties
1.进入hdfs组件
2.hdfs组件中选择操作按钮
3.操作下拉列表中选择 启用high availability
4.配置service名称
5.等待启用完成
6.进入hive组件
7.hive组件选择操作按钮
8.操作下拉列表中选择更新HiveMeTastore NameNode schema
9.等待完成
```

### 5.13 yarn开启HA 

```properties
1.进入yarn组件
2.yarn组件中选择操作按钮
3.操作下拉列表中选择 启用high availability
4.等待启用完成
```

