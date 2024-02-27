package com.library.binhson.documentservice.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-service/demo")
public class DemoController {
    @GetMapping({"","/"})
    public ResponseEntity<?> demo() {return ResponseEntity.ok("ok");}
}
