package com.ReEncrypt.service;

import com.ReEncrypt.dto.CryptoManagerRequestDTO;
import com.ReEncrypt.dto.CryptoManagerResponseDTO;
import com.ReEncrypt.dto.RequestWrapper;
import com.ReEncrypt.dto.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


@Component
public class ReEncrypt
{

    //private static final Logger log = KeymanagerLogger.getLogger(ReEncrypt.class);

    @Autowired
    RestTemplate restTemplate;

    @Value("${cryptoResource.url}")
    public String cryptoResourceUrl;

    @Value("${spring.datasource.driverClassName}")
    public String driverClassName;

    @Value("${spring.datasource.url}")
    public String datasourceUrl;

    @Value("${spring.datasource.username}")
    public String datasourceUserName;

    @Value("${spring.datasource.password}")
    public String dataSourcePassword;

    @Value("${appId}")
    public String appId;

    @Value("${clientId}")
    public String clientId;

    @Value("${secretKey}")
    public String secretKey;

    @Autowired
    private ObjectMapper mapper;

    String token = "";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // to get jdbc connection
    public Connection getConnection() throws SQLException {
        Connection connection = null;
        try {

            Class.forName(driverClassName);

        } catch (ClassNotFoundException e) {
            System.err.println("Unable to find the PostgreSQL JDBC Driver!");
            e.printStackTrace();
            return null;
        }
        try {
            connection =
                    DriverManager.getConnection(datasourceUrl,
                            datasourceUserName, dataSourcePassword);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }

        return connection;

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

    public String getAllReEncrypt(String query) throws SQLException {
        Statement statement;
        System.out.println("PostgreSQL JDBC Connection Testing ~");

        try (Connection connection =getConnection()) {
            ResultSet rs = null;

            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            int row=0;
            while (rs.next() ) {
                System.out.println("row=: "+row++);
                    System.out.println("Pre_Reg_ID = " + rs.getString("prereg_id"));
                   // System.out.println((rs.getBinaryStream("demog_detail")));
                    byte[] b = rs.getBytes("demog_detail");
                    System.out.println("Encrypted demog_detail=\n"+new String(b));

                    System.out.println("demog_detail_hash\n"+rs.getString("demog_detail_hash"));

                    System.out.println("account:-" + rs.getString("cr_by"));

                    if(b.length > 0) {
                        byte[] decrypted = decrypt(b, LocalDateTime.now());
                        System.out.println("decrypted pre-reg-data-:-\n" + new String(decrypted));
                        byte[] ReEncrypted = encrypt(decrypted, LocalDateTime.now());
                        System.out.println("ReEncrypted pre-reg-data-:-\n" + new String(ReEncrypted));
                    }



            }
            //System.out.println(i);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Success";
    }

    public byte[] decrypt(byte[] originalInput, LocalDateTime localDateTime) throws Exception {
//        System.out.println("In decrypt method of CryptoUtil service ");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] decodedBytes = null;
        generateToken("https://qa3.mosip.net");
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
//            System.out.println(
//                    "In decrypt method of CryptoUtil service cryptoResourceUrl: " + cryptoResourceUrl + "/decrypt");
            response = restTemplate.exchange(cryptoResourceUrl + "/decrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
//            if (!(response.getBody().getErrors() == null || response.getBody().getErrors().isEmpty())) {
//                throw new Exception();
//            }
            //System.out.println("myresponse\n"+response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8));
//            decodedBytes = response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8);
            decodedBytes = response.getBody().getResponse().getData().getBytes();
            //decodedBytes = Base64.decodeBase64(response.getBody().getResponse().getData().getBytes());

        } catch (Exception ex) {
//            System.out.println("In decrypt method of CryptoUtil Util for Exception- " + ex.getMessage());
            throw ex;
        }
        return decodedBytes;

    }

    public byte[] encrypt(byte[] originalInput, LocalDateTime localDateTime) {
        //System.out.println("sessionId", "idType", "id", "In encrypt method of CryptoUtil service ");
        generateToken("https://qa-upgrade.mosip.net");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] encryptedBytes = null;
        try {
            //String encodedBytes = io.mosip.kernel.core.util.CryptoUtil.encodeToURLSafeBase64(originalInput);
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("PRE_REGISTRATION");
            //dto.setData(originalInput.toString());
            dto.setData(new String(originalInput, StandardCharsets.UTF_8));
            dto.setReferenceId("INDIVIDUAL");
            dto.setTimeStamp(localDateTime);
            dto.setPrependThumbprint(false);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            //System.out.println("sessionId", "idType", "id",
                    //"In encrypt method of CryptoUtil service cryptoResourceUrl: " + "/encrypt");
            response = restTemplate.exchange( "https://qa-upgrade.mosip.net/v1/keymanager/encrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            //.out.println("sessionId", "idType", "id", "encrypt response of " + response);

//            if (!(response.getBody().getErrors() == null || response.getBody().getErrors().isEmpty())) {
//                throw new EncryptionFailedException(response.getBody().getErrors(), null);
//            }
            encryptedBytes = response.getBody().getResponse().getData().getBytes();

        } catch (Exception ex) {
            //log.debug("sessionId", "idType", "id", ExceptionUtils.getStackTrace(ex));
            //log.error("sessionId", "idType", "id",
                    //"In encrypt method of CryptoUtil Util for Exception- " + ex.getMessage());
            throw ex;
        }
        return encryptedBytes;

    }




    public void start() throws SQLException {
        String query = "SELECT * FROM applicant_demographic";
        System.out.println(getAllReEncrypt(query));


    }

    private List<Map<String, Object>> getTableValue(String query) {

        List<Map<String, Object>> DemographicData = jdbcTemplate.queryForList(query);

        if (DemographicData!=null && !DemographicData.isEmpty()) {
            int row=0;
            for (Map<String, Object> demographicData : DemographicData) {
                if(row++>5)
                    break;
                for (Iterator<Map.Entry<String, Object>> it = demographicData.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Object> entry = it.next();
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    // convert value to byte
                    if(key.equalsIgnoreCase("demog_detail")) {
                        try {
                            byte[] encryptedDemogDetail = value.toString().getBytes();
                            //byte[] decryptedDemogDetail = decrypt(encryptedDemogDetail, LocalDateTime.now());
                            System.out.println(encryptedDemogDetail);
                        } catch (Exception ex) {
                            System.out.println("In getTableValue method of CryptoUtil Util for Exception- " + ex.getMessage());
                        }


                        System.out.println(key + " = " + value);
                    }
                }

                System.out.println();

            }

        }
        return DemographicData;
    }
}