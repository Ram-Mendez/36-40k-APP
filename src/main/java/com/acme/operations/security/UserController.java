package com.acme.operations.security;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserDtos.View> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active) {
        return service.list(page, size, query, active);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.View create(@Valid @RequestBody UserDtos.Create request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserDtos.View update(
            @PathVariable long id, @Valid @RequestBody UserDtos.Update request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable long id) {
        service.deactivate(id);
    }
}
