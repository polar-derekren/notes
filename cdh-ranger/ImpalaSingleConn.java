import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PrivilegedExceptionAction;
import java.sql.*;
import java.util.Properties;

public class ImpalaSingleConn {

    public static Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        InputStream in = Test.class.getClassLoader().getResourceAsStream("default.properties");
        properties.load(new InputStreamReader(in, "UTF-8"));
        return properties;
    }

    public static UserGroupInformation kerberosAuth(Configuration configuration, String krb5Path, String principal, String keytabPath) {
        System.setProperty("java.security.krb5.conf", krb5Path);
        configuration.set("hadoop.security.authentication", "Kerberos");
        UserGroupInformation.setConfiguration(configuration);
        try {
            UserGroupInformation.getLoginUser().checkTGTAndReloginFromKeytab();
            UserGroupInformation.loginUserFromKeytab(principal, keytabPath);
            UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
            System.out.println("loginUser:" + loginUser);
            return loginUser;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Connection getImpalaConn(Properties properties) throws Exception {
        String impalaDriver = properties.getProperty("impala.driver");
        String impalaUrl = properties.getProperty("impala.url");
        String impalaUser = properties.getProperty("impala.user");
        String impalaPwd = properties.getProperty("impala.passwd");
        Class.forName(impalaDriver);
        return DriverManager.getConnection(impalaUrl, impalaUser, impalaPwd);
    }

    public static void main(String[] args) {
        Configuration conf = new Configuration();
        UserGroupInformation loginUser = kerberosAuth(conf, args[1], args[0], args[2]);
        try {
            final Properties properties = loadProperties();
            System.out.println(properties);
            if (loginUser != null) {
                String sql = properties.getProperty("impala.test.sql");
                try (Connection connection = loginUser.doAs((PrivilegedExceptionAction<Connection>) () -> getImpalaConn(properties));
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
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}