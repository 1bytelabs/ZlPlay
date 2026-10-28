// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_gui_extra/juce_gui_extra.h>
namespace performance {
using namespace juce;
inline const Colour background{0xff090f17},panel{0xff101925},line{0xff2a384c},ink{0xffecf1fa},muted{0xff93a9c7},purple{0xffa675ff},cyan{0xff26c9d8};
class Skin final : public LookAndFeel_V4 {
public:
    Skin(){setColour(Label::textColourId,ink);setColour(ComboBox::backgroundColourId,panel);setColour(ComboBox::outlineColourId,line);setColour(ComboBox::textColourId,ink);setColour(ComboBox::arrowColourId,muted);setColour(PopupMenu::backgroundColourId,Colour(0xff101824));setColour(PopupMenu::textColourId,ink);setColour(PopupMenu::highlightedBackgroundColourId,Colour(0xff382b56));setColour(TextEditor::backgroundColourId,panel);setColour(TextEditor::textColourId,ink);setColour(TextEditor::outlineColourId,line);setColour(TextEditor::focusedOutlineColourId,purple);setColour(Slider::textBoxTextColourId,ink);setColour(Slider::textBoxBackgroundColourId,panel);setColour(Slider::textBoxOutlineColourId,line);setColour(ScrollBar::thumbColourId,Colour(0xff465570));}
    Font getTextButtonFont(TextButton&,int h)override{return FontOptions(float(jlimit(11,14,h/3)));}
    Font getComboBoxFont(ComboBox&)override{return FontOptions(13);}
    void drawButtonBackground(Graphics& g,Button& b,const Colour&,bool over,bool down)override{
        auto r=b.getLocalBounds().toFloat().reduced(.6f);bool active=b.getToggleState()||b.getProperties().getWithDefault("primary",false);bool bright=b.getProperties().getWithDefault("cyan",false);auto top=active?Colour(0xffa65bff):Colour(0xff192432),bottom=active?Colour(0xff7337ec):Colour(0xff101824),edge=active?Colour(0xffaf80ff):line;if(bright){top=Colour(0xff26c9d8);bottom=Colour(0xff16b5cf);edge=Colour(0xff45e4e9);}if(down||over)top=top.brighter(.12f);if(!b.isEnabled()){top=top.withAlpha(.35f);bottom=bottom.withAlpha(.35f);edge=edge.withAlpha(.4f);}g.setGradientFill(ColourGradient(top,0,r.getY(),bottom,0,r.getBottom(),false));g.fillRoundedRectangle(r,8);g.setColour(edge);g.drawRoundedRectangle(r,8,.8f);
    }
    void drawButtonText(Graphics& g,TextButton& b,bool,bool)override{g.setFont(getTextButtonFont(b,b.getHeight()));g.setColour((bool(b.getProperties().getWithDefault("cyan",false))?Colour(0xff07151d):ink).withAlpha(b.isEnabled()?1.f:.4f));g.drawFittedText(b.getButtonText(),b.getLocalBounds().reduced(5,1),Justification::centred,1);}
    void drawLinearSlider(Graphics& g,int x,int y,int w,int h,float pos,float,float,Slider::SliderStyle style,Slider& s)override{
        bool vertical=style==Slider::LinearVertical;g.setColour(Colour(0xff060c14));if(vertical){float cx=x+w*.5f;g.fillRoundedRectangle(cx-2,float(y),4,float(h),2);g.setColour(Colour(0xff47546a));g.drawVerticalLine(roundToInt(cx),float(y),float(y+h));g.setGradientFill(ColourGradient(Colour(0xffd1d9e8),0,pos-6,Colour(0xff65758e),0,pos+6,false));g.fillRoundedRectangle(cx-13,pos-6,26,12,3);g.setColour(Colour(0xffe5eaf3));g.drawHorizontalLine(roundToInt(pos),cx-10,cx+10);}else{float cy=y+h*.5f;g.fillRoundedRectangle(float(x),cy-2,float(w),4,2);g.setColour(s.isEnabled()?purple:line);g.fillRoundedRectangle(float(x),cy-2,jmax(0.f,pos-x),4,2);g.fillEllipse(pos-4,cy-4,8,8);}
    }
};
class IconButton final : public TextButton {
public:
    String icon;
    IconButton(String symbol={},String label={}):TextButton(label),icon(symbol){setTitle(label);setTooltip(label);}
    void paintButton(Graphics& g,bool over,bool down)override{
        getLookAndFeel().drawButtonBackground(g,*this,Colours::transparentBlack,over,down);if(icon.isEmpty()){getLookAndFeel().drawButtonText(g,*this,over,down);return;}
        Graphics::ScopedSaveState save(g);auto area=getLocalBounds().toFloat().withSizeKeepingCentre(22,22);g.addTransform(AffineTransform::scale(area.getWidth()/24).translated(area.getX(),area.getY()));g.setColour((bool(getProperties().getWithDefault("cyan",false))?Colour(0xff07151d):ink).withAlpha(isEnabled()?1.f:.35f));Path p;auto symbol=icon=="play"&&getToggleState()?String("pause"):icon;
        if(symbol=="play"){p.addTriangle(8,4,21,12,8,20);g.fillPath(p);}else if(symbol=="pause"){g.fillRoundedRectangle(6,4,4,16,1);g.fillRoundedRectangle(15,4,4,16,1);}else if(symbol=="stop")g.fillRoundedRectangle(5,5,15,15,2);
        else if(symbol=="rewind"){g.drawLine(5,4,5,20,2);p.addTriangle(19,5,7,12,19,20);g.fillPath(p);}else if(symbol=="back"){p.startNewSubPath(15,3);p.lineTo(6,12);p.lineTo(15,21);g.strokePath(p,PathStrokeType(2));}
        else if(symbol=="loop"){g.drawRoundedRectangle(3,5,18,14,5,1.6f);p.startNewSubPath(16,2);p.lineTo(21,5);p.lineTo(17,9);p.startNewSubPath(8,22);p.lineTo(3,19);p.lineTo(7,15);g.strokePath(p,PathStrokeType(1.7f));}
        else if(symbol=="mixer"){for(int i=0;i<3;++i){float x=5.f+i*7;g.drawLine(x,3,x,21,1.5f);float y=i==1?8:15;g.fillRoundedRectangle(x-2,y-2,4,4,1);}}
        else if(symbol=="settings"){g.drawEllipse(5,5,14,14,1.6f);g.drawEllipse(10,10,4,4,1.6f);for(int i=0;i<8;++i){float a=float(i*MathConstants<double>::pi/4);g.drawLine(12+std::cos(a)*8,12+std::sin(a)*8,12+std::cos(a)*11,12+std::sin(a)*11,2);}}
        else if(symbol=="more"){for(int i=0;i<3;++i)g.fillEllipse(float(4+i*7),10,3,3);}
    }
};
class SongCard final : public Button {
public:
    String song,artist,key;Image art;
    SongCard(String name):Button(name),song(name){}
    void paintButton(Graphics& g,bool over,bool down)override{
        auto r=getLocalBounds().toFloat().reduced(1);bool active=getToggleState();g.setGradientFill(ColourGradient(active?Colour(0xff28213e):Colour(0xff121d29),0,0,Colour(0xff0e1722),0,r.getBottom(),false));g.fillRoundedRectangle(r,7);g.setColour(active?purple:over?line.brighter(.4f):line);g.drawRoundedRectangle(r,7,active?1.4f:.7f);auto body=getLocalBounds().reduced(10,5);auto cover=body.removeFromLeft(jmin(48,body.getHeight()));g.setColour(Colour(0xff242238));g.fillRoundedRectangle(cover.toFloat(),5);if(art.isValid())g.drawImageWithin(art,cover.getX()+4,cover.getY()+4,cover.getWidth()-8,cover.getHeight()-8,RectanglePlacement::centred);body.removeFromLeft(9);if(key.isNotEmpty()){auto badge=body.removeFromRight(40).withSizeKeepingCentre(36,25);g.setColour(Colour(0xff29223e));g.fillRoundedRectangle(badge.toFloat(),5);g.setColour(Colour(0xffd9c6ff));g.setFont(FontOptions(12.f));g.drawText(key,badge,Justification::centred);}g.setColour(ink);g.setFont(FontOptions(13).withStyle("Bold"));auto first=body; if(artist.isNotEmpty())first=body.removeFromTop(body.getHeight()/2);g.drawFittedText(song,first,Justification::centredLeft,1);if(artist.isNotEmpty()){g.setColour(muted);g.setFont(FontOptions(11.f));g.drawFittedText(artist,body,Justification::centredLeft,1);}if(down){g.setColour(purple.withAlpha(.12f));g.fillRoundedRectangle(r,7);}
    }
};
class Channel final : public Component {
public:
    Slider& gain;Label name,value;TextButton mute{"M"},solo{"S"},pitch{"Tom"};String group;float level=0;std::function<void()> changed;
    Channel(Slider& fader,String title,String heading,bool pitchControl):gain(fader),group(heading){name.setText(title,dontSendNotification);name.setJustificationType(Justification::centred);name.setFont(FontOptions(11.f));value.setJustificationType(Justification::centred);value.setColour(Label::textColourId,purple);value.setFont(FontOptions(10.f));for(auto* c:std::array<Component*,6>{&gain,&name,&value,&mute,&solo,&pitch})addAndMakeVisible(c);gain.setSliderStyle(Slider::LinearVertical);gain.setTextBoxStyle(Slider::NoTextBox,false,0,0);for(auto* b:{&mute,&solo,&pitch}){b->setClickingTogglesState(true);b->onClick=[this]{if(changed)changed();};}pitch.setVisible(pitchControl);refresh();}
    void refresh(){auto v=gain.getValue();value.setText(v>0?String(20*std::log10(v),1)+" dB":String::fromUTF8("−∞ dB"),dontSendNotification);repaint();}
    void paint(Graphics& g)override{auto r=getLocalBounds().toFloat().reduced(2);g.setColour(Colour(0xff101a26));g.fillRoundedRectangle(r,7);g.setColour(line);g.drawRoundedRectangle(r,7,.7f);g.setColour(group=="VS"?Colour(0xff29203f):Colour(0xff1b2639));g.fillRect(r.withHeight(20));g.setColour(Colour(0xffb4c4e2));g.setFont(FontOptions(9.f));g.drawFittedText(group,r.withHeight(20).toNearestInt(),Justification::centred,1);auto meter=Rectangle<float>(float(getWidth()-11),85,4,float(jmax(10,getHeight()-119)));g.setColour(Colour(0xff060c14));g.fillRoundedRectangle(meter,2);float fraction=level<=.0001f?0:jlimit(0.f,1.f,float((20*std::log10(level)+60)/60));auto filled=meter.withTop(meter.getBottom()-meter.getHeight()*fraction);g.setGradientFill(ColourGradient(Colour(0xffef6964),0,meter.getY(),Colour(0xff28d29a),0,meter.getBottom(),false));g.fillRect(filled);}
    void resized()override{name.setBounds(4,22,getWidth()-8,20);value.setBounds(4,42,getWidth()-8,17);pitch.setBounds((getWidth()-42)/2,61,42,20);gain.setBounds(10,82,getWidth()-23,jmax(15,getHeight()-114));auto row=getLocalBounds().removeFromBottom(29).reduced(8,3);mute.setBounds(row.removeFromLeft(row.getWidth()/2).reduced(2,0));solo.setBounds(row.reduced(2,0));}
};
}
