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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;

//@Component
public class Encrypt {

    private static final Logger log = KeymanagerLogger.getLogger(ReEncrypt.class);

    @Autowired
    RestTemplate restTemplate1;
//
//    @Value("${cryptoResource1.url}")
//    public String cryptoResourceUrl1;

    @Autowired
    private ObjectMapper mapper;

    String token1 = "";

    //@PostConstruct
    public void generateToken() {
        RequestWrapper<ObjectNode> requestWrapper = new RequestWrapper<>();
        ObjectNode request = mapper.createObjectNode();
        request.put("appId", "prereg");
        request.put("clientId", "mosip-prereg-client");
        request.put("secretKey", "abc123");
        requestWrapper.setRequest(request);
        ResponseEntity<ResponseWrapper> response = restTemplate1.postForEntity("https://qa-upgrade.mosip.net/v1/authmanager/authenticate/clientidsecretkey", requestWrapper,
                ResponseWrapper.class);
        token1 = response.getHeaders().getFirst("authorization1");
        restTemplate1.setInterceptors(Collections.singletonList(new ClientHttpRequestInterceptor() {

            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                    throws java.io.IOException {
                request.getHeaders().add(HttpHeaders.COOKIE, "Authorization1=" + token1);
                return execution.execute(request, body);
            }
        }));
    }


    public byte[] encrypt(byte[] originalInput, LocalDateTime localDateTime) {
        log.info("sessionId", "idType", "id", "In encrypt method of CryptoUtil service ");

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
            System.out.println("request"+request);
            response = restTemplate1.exchange( "https://qa-upgrade.mosip.net/v1/keymanager/encrypt", HttpMethod.POST, request,
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
}
