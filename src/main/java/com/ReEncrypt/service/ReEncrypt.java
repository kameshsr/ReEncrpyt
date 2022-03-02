package com.ReEncrypt.service;

import com.ReEncrypt.dto.CryptoManagerRequestDTO;
import com.ReEncrypt.dto.CryptoManagerResponseDTO;
import com.ReEncrypt.dto.RequestWrapper;
import com.ReEncrypt.dto.ResponseWrapper;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;


@Component
public class ReEncrypt
{
//    Logger log = (Logger) LoggerFactory.getLogger(ReEncryptService.class);


    @Autowired
    RestTemplate restTemplate;

    @Value("${cryptoResource.url}")
    public String cryptoResourceUrl;

    public String getAllReEncrypt() throws SQLException {
        Statement statement;
        System.out.println("PostgreSQL JDBC Connection Testing ~");


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
            int i=0;
            while (rs.next()) {
                if (i<218) {
                    System.out.println((rs.getBinaryStream("demog_detail")));
                    byte[] b = rs.getBytes("demog_detail");
                    System.out.println(new String(b));
                    byte[] b1 = rs.getBinaryStream("demog_detail").toString().getBytes();
                    System.out.println(new String(b1));
                    System.out.println(b1);

                    System.out.println("decrypted data" + rs.getString("cr_by"));
                    byte[] decrypted = decrypt(b, LocalDateTime.now());

                }
                i++;
            }
            System.out.println(i);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public byte[] decrypt(byte[] originalInput, LocalDateTime localDateTime) throws Exception {
//        log.info("In decrypt method of CryptoUtil service ");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] decodedBytes = null;
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
            decodedBytes = Base64.decodeBase64(response.getBody().getResponse().getData().getBytes());

        } catch (Exception ex) {
//            log.info("In decrypt method of CryptoUtil Util for Exception- " + ex.getMessage());
            throw ex;
        }
        return decodedBytes;

    }

    public void start() throws SQLException {
        System.out.println(getAllReEncrypt());
    }
}