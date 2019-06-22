import http from "k6/http";
import {
  check
} from "k6";

export let options = {
  vus: 10,
  duration: "10s"
};

export default function () {
  let res = http.get("http://127.0.0.1:8080/cars");
  check(res, {
    "status was 200": (r) => r.status == 200,
    "transaction time OK": (r) => r.timings.duration < 500
  });
};