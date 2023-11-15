import com.zaxxer.hikari.HikariDataSource;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PrivilegedExceptionAction;
import java.sql.*;
import java.util.Properties;

public class ImpalaDataSourceConn {

    public static Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        InputStream in = Test.class.getClassLoader().getResourceAsStream("default.properties");
        properties.load(new InputStreamReader(in, "UTF-8"));
        return properties;
    }

    public static KerberosDataSource getImpalaConn(Properties properties) {
        String impalaDriver = properties.getProperty("impala.driver");
        String impalaUrl = properties.getProperty("impala.url");
        String impalaUser = properties.getProperty("impala.user");
        String impalaPwd = properties.getProperty("impala.passwd");
        KerberosDataSource dataSource = new KerberosDataSource();
        dataSource.setDriverClassName(impalaDriver);
        dataSource.setJdbcUrl(impalaUrl);
        dataSource.setUsername(impalaUser);
        dataSource.setPassword(impalaPwd);
        dataSource.setMinimumIdle(0);
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }

    public static void main(String[] args) {
        try {
            final Properties properties = loadProperties();
            System.out.println(properties);
            String sql = properties.getProperty("impala.test.sql");
            try (KerberosDataSource dataSource = getImpalaConn(properties)) {
                dataSource.setPrincipal(args[0]);
                dataSource.setKrb5Path(args[1]);
                dataSource.setKeytabPath(args[2]);
                for (int j = 0; j < 10; j++) {
                    System.out.println(j);
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement ps = connection.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData md = rs.getMetaData();
                        while (rs.next()) {
                            for (int i = 1; i <= md.getColumnCount(); i++) {
                                System.out.print(String.format("%s -> %s,", md.getColumnName(i), rs.getObject(i)));
                            }
                            System.out.println();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}

class KerberosDataSource extends HikariDataSource {

    private String krb5Path;
    private String principal;
    private String keytabPath;
    private UserGroupInformation loginUser;

    public void setKrb5Path(String krb5Path) {
        this.krb5Path = krb5Path;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public void setKeytabPath(String keytabPath) {
        this.keytabPath = keytabPath;
    }

    private UserGroupInformation kerberosAuth(Configuration configuration, String krb5Path, String principal, String keytabPath) {
        // 设置krb5文件绝对路径
        System.setProperty("java.security.krb5.conf", krb5Path);
        configuration.set("hadoop.security.authentication", "Kerberos");
        UserGroupInformation.setConfiguration(configuration);
        try {
            // 设置TGT自动刷新
            UserGroupInformation.getLoginUser().checkTGTAndReloginFromKeytab();
            // 登录
            UserGroupInformation.loginUserFromKeytab(principal, keytabPath);
            // 获取当前登录用户
            UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
            System.out.println("loginUser:" + loginUser);
            return loginUser;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Connection getConnection() {
        if (loginUser == null) {
            loginUser = kerberosAuth(new Configuration(), krb5Path, principal, keytabPath);
        }
        if (loginUser != null) {
            try {
                return loginUser.doAs((PrivilegedExceptionAction<Connection>) super::getConnection);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}