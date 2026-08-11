package com.sucre.surena.service;

import com.sucre.surena.dto.LoginRequest;
import com.sucre.surena.dto.LoginResponse;
import com.sucre.surena.dto.RegisterRequest;
import com.sucre.surena.config.JwtService;
import com.sucre.surena.entity.Usuario;
import com.sucre.surena.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername()).orElseThrow();
        String token = jwtService.generateToken(request.getUsername(), usuario.getRol());
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(usuario.getUsername())
                .rol(usuario.getRol())
                .nombre(usuario.getNombre())
                .expiresIn(86400000L)
                .build();
    }

    @Transactional
    public Usuario registrar(RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("El usuario ya existe: " + request.getUsername());
        }
        Usuario usuario = Usuario.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .rol(request.getRol() != null && !request.getRol().isBlank() ? request.getRol() : "USER")
                .activo(true)
                .build();
        return usuarioRepository.save(usuario);
    }
}
