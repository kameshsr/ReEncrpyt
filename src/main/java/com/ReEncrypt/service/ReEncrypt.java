package com.ReEncrypt.service;

import com.ReEncrypt.dto.CryptoManagerRequestDTO;
import com.ReEncrypt.dto.CryptoManagerResponseDTO;
import com.ReEncrypt.dto.RequestWrapper;
import com.ReEncrypt.dto.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.kernel.core.util.DateUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.TimeZone;

import io.mosip.kernel.core.util.HMACUtils;
import io.mosip.kernel.core.util.HashUtils;


@Component
public class ReEncrypt
{

    Logger logger = org.slf4j.LoggerFactory.getLogger(ReEncrypt.class);

    @Autowired
    RestTemplate restTemplate;

    @Value("${cryptoResource.url}")
    public String cryptoResourceUrl;

    @Value("${sourceDriverClassName}")
    public String sourceDriverClassName;

    @Value("${sourceDatasourceUrl}")
    public String sourceDatasourceUrl;

    @Value("${sourceDatasourceUserName}")
    public String sourceDatasourceUserName;

    @Value("${sourceDataSourcePassword}")
    public String sourceDataSourcePassword;

    @Value("${targetDriverClassName}")
    public String targetDriverClassName;

    @Value("${targetDatasourceUrl}")
    public String targetDatasourceUrl;

    @Value("${targetDatasourceUserName}")
    public String targetDatasourceUserName;

    @Value("${targetDataSourcePassword}")
    public String targetDataSourcePassword;

    @Value("${appId}")
    public String appId;

    @Value("${clientId}")
    public String clientId;

    @Value("${secretKey}")
    public String secretKey;

    @Value("${decryptBaseUrl}")
    public String decryptBaseUrl;

    @Value("${encryptBaseUrl}")
    public String encryptBaseUrl;

    @Autowired
    private ObjectMapper mapper;

    String token = "";

    public Connection sourceDatabaseConnection =null;

    public Connection targetDatabaseConnection =null;


    public Connection getConnection() throws SQLException {
        sourceDatabaseConnection = null;
        try {
            Class.forName(sourceDriverClassName);
        } catch (ClassNotFoundException e) {
            logger.error("Error while loading driver class", e);
            e.printStackTrace();
            return null;
        }
        try {
            sourceDatabaseConnection =
                    DriverManager.getConnection(sourceDatasourceUrl,
                            sourceDatasourceUserName, sourceDataSourcePassword);
            targetDatabaseConnection = DriverManager.getConnection(targetDatasourceUrl,
                    targetDatasourceUserName, targetDataSourcePassword);
        }
        catch (Exception e){
            logger.error(e.getMessage());
        }
        return sourceDatabaseConnection;
    }

    public void generateToken(String url) {
        RequestWrapper<ObjectNode> requestWrapper = new RequestWrapper<>();
        ObjectNode request = mapper.createObjectNode();
        request.put("appId", appId);
        request.put("clientId", clientId);
        request.put("secretKey", secretKey);
        requestWrapper.setRequest(request);
        ResponseEntity<ResponseWrapper> response = restTemplate.postForEntity(url+"/v1/authmanager/authenticate/clientidsecretkey", requestWrapper,
                ResponseWrapper.class);
        token = response.getHeaders().getFirst("authorization");
        restTemplate.setInterceptors(Collections.singletonList(new ClientHttpRequestInterceptor() {

            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                    throws java.io.IOException {
                request.getHeaders().add(HttpHeaders.COOKIE, "Authorization=" + token);
                return execution.execute(request, body);
            }
        }));
    }

    public String reEncryptDatabaseValues(String query) throws SQLException {
        logger.info("PostgreSQL JDBC Connection Testing ~");

        try (Connection connection =getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            int row = 0;
            while (rs.next()) {
                logger.info("row=: " + row++);
                logger.info("Pre_Reg_ID = " + rs.getString("prereg_id"));
                byte[] demog_details = rs.getBytes("demog_detail");
                logger.info("Encrypted demog_detail=\n" + new String(demog_details));
                logger.info("demog_detail_hash\n" + rs.getString("demog_detail_hash"));
                logger.info("account:-" + rs.getString("cr_by"));
                if (demog_details.length > 0) {
                    byte[] decrypted = decrypt(demog_details, LocalDateTime.now(), decryptBaseUrl);
                    if(decrypted == null) {
                        logger.info("Decrypted data is null");
                        continue;
                    }
                    logger.info("decrypted pre-reg-data-:-\n" + new String(decrypted));
                    byte[] ReEncrypted = encrypt(decrypted, LocalDateTime.now(), encryptBaseUrl);
                    logger.info("ReEncrypted pre-reg-data-:-\n" + new String(ReEncrypted));

                    try {String updateQuery =
                                "update applicant_demographic set demog_detail=?, demog_detail_hash=?, encrypted_dtimes=?::timestamp where prereg_id=?";
                        PreparedStatement stmt = targetDatabaseConnection.prepareStatement(updateQuery);
                            stmt.setBytes(1, ReEncrypted);

                            stmt.setString(2, hashUtill(ReEncrypted));

                        LocalDateTime encryptionDateTime = DateUtils.getUTCCurrentDateTime();
                            stmt.setTimestamp(3, Timestamp.valueOf(encryptionDateTime));
                            stmt.setString(4, rs.getString("prereg_id"));
                            stmt.executeUpdate();
                            logger.info("Updated");
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
            logger.info("Total rows=: " + row);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Success";
    }

    public byte[] decrypt(byte[] originalInput, LocalDateTime localDateTime, String decryptBaseUrl) throws Exception {
        logger.info("In decrypt method of CryptoUtil service ");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] decodedBytes = null;
        generateToken(decryptBaseUrl);
        try {
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("REGISTRATION");
            dto.setData(new String(originalInput, StandardCharsets.UTF_8));
            dto.setReferenceId("");
            dto.setTimeStamp(localDateTime);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            logger.info("In decrypt method of CryptoUtil service cryptoResourceUrl: " + cryptoResourceUrl + "/decrypt");
            response = restTemplate.exchange(cryptoResourceUrl + "/decrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            logger.info("myresponse\n"+response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8));
            decodedBytes = response.getBody().getResponse().getData().getBytes();
        } catch (Exception ex) {
            logger.error("Error in decrypt method of CryptoUtil service " + ex.getMessage());
        }
        return decodedBytes;
    }

    public byte[] encrypt(byte[] originalInput, LocalDateTime localDateTime, String encryptBaseUrl) {
        logger.info("sessionId", "idType", "id", "In encrypt method of CryptoUtil service ");
        generateToken(encryptBaseUrl);
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] encryptedBytes = null;
        try {
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("PRE_REGISTRATION");
            dto.setData(new String(originalInput, StandardCharsets.UTF_8));
            dto.setReferenceId("INDIVIDUAL");
            dto.setTimeStamp(localDateTime);
            dto.setPrependThumbprint(false);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            logger.info("sessionId", "idType", "id",
                    "In encrypt method of CryptoUtil service cryptoResourceUrl: " + "/encrypt");
            response = restTemplate.exchange( encryptBaseUrl+"/v1/keymanager/encrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            encryptedBytes = response.getBody().getResponse().getData().getBytes();
        } catch (Exception ex) {
            logger.error("sessionId", "idType", "id", "Error in encrypt method of CryptoUtil service " + ex.getMessage());
        }
        return encryptedBytes;
    }

    public void start() throws SQLException {
        String query = "SELECT * FROM applicant_demographic";
        System.out.println(reEncryptDatabaseValues(query));
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        printTableValues(query);
    }

    private void printTableValues(String query) {
        try (Connection connection =getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            int row = 0;
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            while (rs.next()) {
                logger.info(new String(rs.getBytes("demog_detail")));
                logger.info(rs.getString("prereg_id"));
                logger.info(rs.getString("demog_detail_hash"));
                logger.info(String.valueOf(rs.getTimestamp("encrypted_dtimes")));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String hashUtill(byte[] bytes) {
        return HMACUtils.digestAsPlainText(HMACUtils.generateHash(bytes));
    }
}