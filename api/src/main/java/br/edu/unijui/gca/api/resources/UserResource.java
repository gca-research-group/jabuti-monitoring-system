package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.dtos.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.services.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController()
@RequestMapping("/user")
public class UserResource extends BaseResource<User, Long, UserDto, UserFilterDto, UserService>{
}
