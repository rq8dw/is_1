package ru.itmo.is.lab1.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.is.lab1.domain.OrganizationType;
import ru.itmo.is.lab1.dto.AbsorbRequest;
import ru.itmo.is.lab1.dto.AbsorbResult;
import ru.itmo.is.lab1.dto.CountResult;
import ru.itmo.is.lab1.dto.OrganizationDto;
import ru.itmo.is.lab1.service.SpecialOperationsService;

import java.util.List;

@RestController
@RequestMapping("/api/special")
public class SpecialOperationsController {

    private final SpecialOperationsService specialOperationsService;

    public SpecialOperationsController(SpecialOperationsService specialOperationsService) {
        this.specialOperationsService = specialOperationsService;
    }

    @GetMapping("/count-by-type")
    public CountResult countByType(@RequestParam OrganizationType type) {
        return new CountResult(specialOperationsService.countByType(type));
    }

    @GetMapping("/count-full-name-greater")
    public CountResult countFullNameGreater(@RequestParam String fullName) {
        return new CountResult(specialOperationsService.countFullNameGreaterThan(fullName));
    }

    @GetMapping("/type-less-than")
    public List<OrganizationDto> typeLessThan(@RequestParam OrganizationType type) {
        return specialOperationsService.findTypeLessThan(type);
    }

    @PostMapping("/organizations/{id}/employees")
    public OrganizationDto addEmployee(@PathVariable long id) {
        return specialOperationsService.addEmployee(id);
    }

    @PostMapping("/absorb")
    public AbsorbResult absorb(@Valid @RequestBody AbsorbRequest request) {
        return specialOperationsService.absorb(request.absorberId(), request.absorbedId());
    }
}
