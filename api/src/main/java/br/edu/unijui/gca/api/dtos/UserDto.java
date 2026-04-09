package br.edu.unijui.gca.api.dtos;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserDto extends BaseDto<Long>{
    private String name;
    private String password;
    private String email;
    private String photo;
    private Boolean status;
}
