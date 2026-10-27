package br.edu.unijui.gca.api.identity.adapters.http.dto;

public record UserResponse(Long id, String name, String email, String photo, Boolean status, String remarks) { }
