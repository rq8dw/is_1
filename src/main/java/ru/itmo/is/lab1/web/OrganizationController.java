package ru.itmo.is.lab1.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.itmo.is.lab1.domain.OrganizationType;
import ru.itmo.is.lab1.dto.OrganizationDto;
import ru.itmo.is.lab1.dto.OrganizationRequest;
import ru.itmo.is.lab1.dto.PageDto;
import ru.itmo.is.lab1.service.OrganizationFilter;
import ru.itmo.is.lab1.service.OrganizationService;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public PageDto<OrganizationDto> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(defaultValue = "id") String sort,
                                         @RequestParam(defaultValue = "asc") String direction,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String fullName,
                                         @RequestParam(required = false) OrganizationType type,
                                         @RequestParam(required = false) String officialZipCode,
                                         @RequestParam(required = false) String postalZipCode) {
        OrganizationFilter filter = new OrganizationFilter(name, fullName, type, officialZipCode, postalZipCode);
        return organizationService.findPage(filter, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public OrganizationDto get(@PathVariable long id) {
        return organizationService.get(id);
    }

    @PostMapping
    public ResponseEntity<OrganizationDto> create(@Valid @RequestBody OrganizationRequest request) {
        OrganizationDto created = organizationService.create(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public OrganizationDto update(@PathVariable long id, @Valid @RequestBody OrganizationRequest request) {
        return organizationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
