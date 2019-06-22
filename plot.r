colors <- c("lightcoral", "lightgreen", "lightblue")


writeImage <- function(imageName) {
jpeg(filename = paste("img/",imageName),
     width = 960, height = 960, units = "px", pointsize = 24,
     quality = 300,
     bg = "white");
} 


rps_sse <- read.table("./rps_sse.csv", header=T, sep=",");
writeImage("rps_sse_fast.jpeg");
barplot(rps_sse$fast, main="RPS mit Quarkus", ylab= "amount/s",
   beside=TRUE, col=colors, names=c("fast"));
legend("topright", c("blocking", "stream", "sse"), cex=0.6,
   bty="n", fill=colors);

writeImage("rps_sse_slow.jpeg");
barplot(rps_sse$slow, main="RPS mit Quarkus", ylab= "amount/s",
   beside=TRUE, col=colors, names=c("slow"));
legend("topright", c("blocking", "stream", "sse"), cex=0.6,
   bty="n", fill=colors);
