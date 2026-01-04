package com.example.virtualthreaddemo.controllerr;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {

    @GetMapping("/light-io")
    public String lightIO() throws InterruptedException {
        Thread.sleep(100);
        return "OK";
    }

    @GetMapping("/heavy-io")
    public String heavyIO() throws InterruptedException {
        Thread.sleep(500);
        return "OK";
    }

    @GetMapping("/info")
    public String info() {
        Thread t = Thread.currentThread();
        return String.format(
                "Thread: %s, Virtual: %s, ID: %d",
                t.getName(),
                t.isVirtual(),
                t.threadId()
        );
    }
}