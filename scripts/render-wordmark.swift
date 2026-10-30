// Renders the launch-screen wordmark "Mọyì" (Newsreader Medium, tokens.md §6) as
// outlined glyph paths into a one-page vector PDF: no font is embedded, so the
// asset needs nothing at runtime. Regenerate both appearances with:
//   F=shared/designsystem/src/commonMain/composeResources/font/newsreader_medium.ttf
//   D=iosApp/iosApp/Assets.xcassets/LaunchWordmark.imageset
//   swift scripts/render-wordmark.swift $F 48 2A2020 $D/wordmark-light.pdf   # ink, light
//   swift scripts/render-wordmark.swift $F 48 F8EFE9 $D/wordmark-dark.pdf    # ink, dark
// Size 48: Figma's splash wordmark (379:5) sits in a 48-high box, and Newsreader's
// ascent + descent is exactly one em, so 48 fills it; it is also the 48 px minimum
// the logo must hold (19 §6). The box is the advance width (107) by 48.
import CoreText
import CoreGraphics
import Foundation

let args = CommandLine.arguments
let descs = CTFontManagerCreateFontDescriptorsFromURL(URL(fileURLWithPath: args[1]) as CFURL) as! [CTFontDescriptor]
let size = CGFloat(Double(args[2])!)
let hex = UInt32(args[3], radix: 16)!
let font = CTFontCreateWithFontDescriptor(descs[0], size, nil)
let attr = NSAttributedString(string: "M\u{1ECD}y\u{00EC}", attributes: [kCTFontAttributeName as NSAttributedString.Key: font])
let line = CTLineCreateWithAttributedString(attr)
var ascent: CGFloat = 0, descent: CGFloat = 0, leading: CGFloat = 0
let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, &leading))
// The box is the advance width by ascent + descent (= one em for Newsreader), baseline at the descent.
var box = CGRect(x: 0, y: 0, width: (width * 100).rounded(.up) / 100, height: ascent + descent)
let ctx = CGContext(URL(fileURLWithPath: args[4]) as CFURL, mediaBox: &box, nil)!
ctx.beginPDFPage(nil)
ctx.setFillColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
let path = CGMutablePath()
for run in CTLineGetGlyphRuns(line) as! [CTRun] {
    let count = CTRunGetGlyphCount(run)
    var glyphs = [CGGlyph](repeating: 0, count: count)
    var positions = [CGPoint](repeating: .zero, count: count)
    CTRunGetGlyphs(run, CFRange(location: 0, length: count), &glyphs)
    CTRunGetPositions(run, CFRange(location: 0, length: count), &positions)
    for i in 0..<count {
        if let g = CTFontCreatePathForGlyph(font, glyphs[i], nil) {
            path.addPath(g, transform: CGAffineTransform(translationX: positions[i].x, y: positions[i].y + descent))
        }
    }
}
ctx.addPath(path)
ctx.fillPath()
ctx.endPDFPage()
ctx.closePDF()
print("wrote", args[4], box)
