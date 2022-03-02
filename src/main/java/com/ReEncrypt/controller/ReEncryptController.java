package com.ReEncrypt.controller;

import com.ReEncrypt.service.ReEncrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

//mark class as Controller
@RestController
public class ReEncryptController
{
//autowire the BooksService class
@Autowired
ReEncrypt reEncryptService;

@GetMapping("/")
private String getAllReEncrypt() throws SQLException {
    return reEncryptService.getAllReEncrypt();
}
}
