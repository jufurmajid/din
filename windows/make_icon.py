from PIL import Image, ImageDraw

S = 512
img = Image.new("RGBA", (S, S), (11, 18, 32, 255))
d = ImageDraw.Draw(img)
# rounded inner panel
d.rounded_rectangle((56, 56, 456, 456), radius=92, fill=(16, 42, 86, 255))
# ledger/book
d.rounded_rectangle((128, 112, 360, 386), radius=28, fill=(244, 185, 66, 255))
d.rounded_rectangle((152, 136, 336, 362), radius=18, fill=(255, 255, 255, 255))
# binding
d.rounded_rectangle((112, 128, 150, 370), radius=16, fill=(37, 99, 235, 255))
# lines
for y in (188, 238, 288):
    d.rounded_rectangle((188, y, 306, y + 14), radius=7, fill=(16, 42, 86, 255))
# green verified badge
d.ellipse((300, 304, 424, 428), fill=(16, 185, 129, 255), outline=(255, 255, 255, 255), width=10)
d.line((329, 365, 354, 390, 397, 339), fill=(255, 255, 255, 255), width=16, joint="curve")
img.save("windows/app.ico", sizes=[(256,256),(128,128),(64,64),(48,48),(32,32),(16,16)])
print("ICON_OK")
