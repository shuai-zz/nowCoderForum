package com.example.user.application.dto;

import lombok.*;

import java.util.Date;

/**
 * @author 23211
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginTicket {
    private int id;
    private int userId;
    private String ticket;
    private int status;
    private Date expired;
}
