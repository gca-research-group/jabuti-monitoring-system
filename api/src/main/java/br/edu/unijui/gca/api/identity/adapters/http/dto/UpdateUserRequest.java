package br.edu.unijui.gca.api.identity.adapters.http.dto;

import br.edu.unijui.gca.api.shared.application.UpdateField;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class UpdateUserRequest extends IdentityRequest {
    private UpdateField<String> name = UpdateField.absent();
    private UpdateField<String> email = UpdateField.absent();
    private UpdateField<String> password = UpdateField.absent();
    private UpdateField<String> photo = UpdateField.absent();
    private UpdateField<Boolean> status = UpdateField.absent();
    private UpdateField<String> remarks = UpdateField.absent();

    @JsonSetter("name")
    public void setName(String value) {
        if (value == null || value.isBlank()) throw new InvalidField("name");
        name = UpdateField.supplied(value);
    }

    @JsonSetter("email")
    public void setEmail(String value) {
        if (value == null || value.isBlank()) throw new InvalidField("email");
        email = UpdateField.supplied(value);
    }

    @JsonSetter("password")
    public void setPassword(String value) {
        if (value == null || value.isEmpty()) throw new InvalidField("password");
        password = UpdateField.supplied(value);
    }

    @JsonSetter("photo")
    public void setPhoto(String value) { photo = UpdateField.supplied(value); }

    @JsonSetter("status")
    public void setStatus(Boolean value) {
        if (value == null) throw new InvalidField("status");
        status = UpdateField.supplied(value);
    }

    @JsonSetter("remarks")
    public void setRemarks(String value) { remarks = UpdateField.supplied(value); }

    @Email
    public String getEmail() { return email.value(); }

    @Size(max = 255)
    public String getPhoto() { return photo.value(); }

    // Presence accessors are not JSON properties.
    public UpdateField<String> name() { return name; }
    public UpdateField<String> email() { return email; }
    public UpdateField<String> password() { return password; }
    public UpdateField<String> photo() { return photo; }
    public UpdateField<Boolean> status() { return status; }
    public UpdateField<String> remarks() { return remarks; }
}
