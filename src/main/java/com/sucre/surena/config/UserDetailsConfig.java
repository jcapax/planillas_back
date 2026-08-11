package com.sucre.surena.config;

import com.sucre.surena.entity.Usuario;
import com.sucre.surena.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class UserDetailsConfig {

    private final UsuarioRepository usuarioRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            Usuario usuario = usuarioRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
            if (!Boolean.TRUE.equals(usuario.getActivo())) {
                throw new UsernameNotFoundException("Usuario inactivo: " + username);
            }
            return new User(
                    usuario.getUsername(),
                    usuario.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol()))
            );
        };
    }
}
