package ru.itmo.is.lab1.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.is.lab1.dto.AddressDto;
import ru.itmo.is.lab1.dto.CoordinatesDto;
import ru.itmo.is.lab1.service.RelatedObjectService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RelatedObjectController {

    private final RelatedObjectService relatedObjectService;

    public RelatedObjectController(RelatedObjectService relatedObjectService) {
        this.relatedObjectService = relatedObjectService;
    }

    @GetMapping("/coordinates")
    public List<CoordinatesDto> coordinates() {
        return relatedObjectService.listCoordinates();
    }

    @DeleteMapping("/coordinates/{id}")
    public ResponseEntity<Void> deleteCoordinates(@PathVariable long id) {
        relatedObjectService.deleteCoordinates(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses")
    public List<AddressDto> addresses() {
        return relatedObjectService.listAddresses();
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable long id) {
        relatedObjectService.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }
}
