import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let src = CommandLine.arguments[1]
let dst = CommandLine.arguments[2]

guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: src) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("cannot read \(src)")
}

let w = image.width, h = image.height
// noneSkipLast = opaque RGB with a padding byte: no alpha channel in the output.
guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                          space: CGColorSpaceCreateDeviceRGB(),
                          bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
    fatalError("cannot create context")
}
// White beneath, so any transparent pixels flatten to white rather than black.
ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

guard let out = ctx.makeImage(),
      let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: dst) as CFURL, UTType.png.identifier as CFString, 1, nil) else {
    fatalError("cannot write")
}
CGImageDestinationAddImage(dest, out, nil)
CGImageDestinationFinalize(dest)
print("wrote \(dst) \(w)x\(h)")
