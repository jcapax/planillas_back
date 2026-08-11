package com.sucre.surena.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String token;
    private String tokenType;
    private String username;
    private String rol;
    private String nombre;
    private long expiresIn;
}
