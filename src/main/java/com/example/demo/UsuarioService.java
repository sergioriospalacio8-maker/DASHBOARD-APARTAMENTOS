package com.example.demo;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // Crear un nuevo usuario
    public Usuario save(Usuario usuario) {
        usuario.ensureId();
        return usuarioRepository.save(usuario);
    }

    // Obtener un usuario por ID
    public Usuario findeById(String id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    //Listar todos los usuarios
    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    //Buscar por nombre
    public List<Usuario> findByNombre(String nombre) {
        return findAll().stream()
                .filter(usuario -> usuario.getNombre().toLowerCase().contains(nombre.toLowerCase()))
                .toList();
    }

    //Actualizar un usuario
    @Transactional
    public Usuario update(String id, Usuario cambios) {
        Usuario usuario = findeById(id);
        if (usuario != null) {
            usuario.setNombre(cambios.getNombre());
            usuario.setEmail(cambios.getEmail());
            usuario.setEdad(cambios.getEdad());
        }
        return usuario;
    }

    // Actualizacion parcial
    @Transactional
    public Usuario patch(String id, Map<String, Object> updates) {
        Usuario usuario = findeById(id);
        if (usuario != null) {
            updates.forEach((key, value) -> {
                switch (key) {

                    case "nombre" -> usuario.setNombre((String) value);
                    case "email" -> usuario.setEmail((String) value);
                    case "edad" -> usuario.setEdad(((Number) value).intValue());
                    default -> {
                        // Ignore fields not supported by the partial update contract.
                    }
                }
            });
        }
        return usuario;
    }

    // Eliminar un usuario
    @Transactional
    public boolean deleteById(String id) {
        Usuario usuario = findeById(id);
        if (usuario == null) {
            return false;
        }
        usuarioRepository.delete(usuario);
        return true;
    }
}
