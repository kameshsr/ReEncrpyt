package com.ReEncrypt.service;

import com.ReEncrypt.dto.CryptoManagerRequestDTO;
import com.ReEncrypt.dto.CryptoManagerResponseDTO;
import com.ReEncrypt.dto.RequestWrapper;
import com.ReEncrypt.dto.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.keymanagerservice.logger.KeymanagerLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Collections;


@Component
public class ReEncrypt
{

    private static final Logger log = KeymanagerLogger.getLogger(ReEncrypt.class);

    @Autowired
    RestTemplate restTemplate;

    @Value("${cryptoResource.url}")
    public String cryptoResourceUrl;

    @Autowired
    private ObjectMapper mapper;

    String token = "";

    //@PostConstruct
    public void generateToken(String url) {
        RequestWrapper<ObjectNode> requestWrapper = new RequestWrapper<>();
        ObjectNode request = mapper.createObjectNode();
        request.put("appId", "prereg");
        request.put("clientId", "mosip-prereg-client");
        request.put("secretKey", "abc123");
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

    public String getAllReEncrypt() throws SQLException {
        Statement statement;
        log.info("PostgreSQL JDBC Connection Testing ~");


        try {

            Class.forName("org.postgresql.Driver");

        } catch (ClassNotFoundException e) {
            System.err.println("Unable to find the PostgreSQL JDBC Driver!");
            e.printStackTrace();
            return null;
        }


        try (Connection connection =
                     DriverManager.getConnection("jdbc:postgresql://qa3.mosip.net:30090/mosip_prereg",
                             "postgres", "mosip123")) {
            ResultSet rs = null;
            String query = "SELECT * FROM applicant_demographic";
            statement = connection.createStatement();
            rs = statement.executeQuery(query);
            int row=0;
            while (rs.next() && row++<5) {
                log.info("row=: "+row++);
                    log.info("Pre_Reg_ID = " + rs.getString("prereg_id"));
                   // log.info((rs.getBinaryStream("demog_detail")));
                    byte[] b = rs.getBytes("demog_detail");
                    log.info("Encrypted demog_detail=\n"+new String(b));
                    byte[] b1 = rs.getBinaryStream("demog_detail").toString().getBytes();
                    //log.info(new String(b1));
                    log.info("demog_detail_hash\n"+rs.getString("demog_detail_hash"));

                    log.info("account:-" + rs.getString("cr_by"));
                byte[] decrypted;
                byte[] ReEncrypted;
                Encrypt encrypt = new Encrypt();
                    if(b.length > 0) {
                        decrypted = decrypt(b, LocalDateTime.now());
                        log.info("decrypted pre-reg-data-:-\n" + new String(decrypted));
                        ReEncrypted = encrypt.encrypt(decrypted, LocalDateTime.now());
//                        log.info("ReEncrypted pre-reg-data-:-\n" + new String(ReEncrypted));
                    }



            }
            //log.info(i);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Success";
    }

    public byte[] decrypt(byte[] originalInput, LocalDateTime localDateTime) throws Exception {
//        log.info("In decrypt method of CryptoUtil service ");
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
//            log.info(
//                    "In decrypt method of CryptoUtil service cryptoResourceUrl: " + cryptoResourceUrl + "/decrypt");
            response = restTemplate.exchange(cryptoResourceUrl + "/decrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
//            if (!(response.getBody().getErrors() == null || response.getBody().getErrors().isEmpty())) {
//                throw new Exception();
//            }
            //log.info("myresponse\n"+response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8));
            decodedBytes = response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8);
            //decodedBytes = Base64.decodeBase64(response.getBody().getResponse().getData().getBytes());

        } catch (Exception ex) {
//            log.info("In decrypt method of CryptoUtil Util for Exception- " + ex.getMessage());
            throw ex;
        }
        return decodedBytes;

    }

    public byte[] encrypt(byte[] originalInput, LocalDateTime localDateTime) {
        log.info("sessionId", "idType", "id", "In encrypt method of CryptoUtil service ");
        generateToken("https://qa-upgrade.mosip.net");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] encryptedBytes = null;
        try {
            String encodedBytes = io.mosip.kernel.core.util.CryptoUtil.encodeToURLSafeBase64(originalInput);
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("PRE_REGISTRATION");
            dto.setData(encodedBytes);
            dto.setReferenceId("INDIVIDUAL");
            dto.setTimeStamp(localDateTime);
            dto.setPrependThumbprint(false);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            log.info("sessionId", "idType", "id",
                    "In encrypt method of CryptoUtil service cryptoResourceUrl: " + "/encrypt");
            response = restTemplate.exchange( "https://qa-upgrade.mosip.net/v1/keymanager/encrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            log.info("sessionId", "idType", "id", "encrypt response of " + response);

//            if (!(response.getBody().getErrors() == null || response.getBody().getErrors().isEmpty())) {
//                throw new EncryptionFailedException(response.getBody().getErrors(), null);
//            }
            encryptedBytes = response.getBody().getResponse().getData().getBytes();

        } catch (Exception ex) {
            log.debug("sessionId", "idType", "id", ExceptionUtils.getStackTrace(ex));
            log.error("sessionId", "idType", "id",
                    "In encrypt method of CryptoUtil Util for Exception- " + ex.getMessage());
            throw ex;
        }
        return encryptedBytes;

    }




    public void start() throws SQLException {
        log.info(getAllReEncrypt());
    }
}