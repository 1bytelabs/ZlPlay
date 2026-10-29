// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "PerformanceUI.h"
namespace performance {
class Stack final : public Component {
public:
    struct Item {Component* component;int height;};std::vector<Item> items;int gap=8,padding=0;
    void append(Component& c,int h){items.push_back({&c,h});addAndMakeVisible(c);}
    int idealHeight()const{int h=padding*2;for(auto& i:items)h+=i.height+gap;return jmax(0,h-gap);}
    void resized()override{auto r=getLocalBounds().reduced(padding);for(auto& i:items){i.component->setBounds(r.removeFromTop(i.height));r.removeFromTop(gap);}}
};
class Line final : public Component {
public:
    struct Item{Component* component;int width;};std::vector<Item> items;int gap=6;
    void append(Component& c,int width=0){items.push_back({&c,width});addAndMakeVisible(c);}
    void resized()override{auto r=getLocalBounds();int fixed=gap*jmax(0,int(items.size())-1),flex=0;for(auto& i:items)if(i.width>0)fixed+=i.width;else ++flex;int w=jmax(0,r.getWidth()-fixed)/jmax(1,flex);for(auto& i:items){i.component->setBounds(r.removeFromLeft(i.width>0?i.width:w));r.removeFromLeft(gap);}}
};
class Section final : public Component {
public:
    Stack body;Section(){addAndMakeVisible(body);body.padding=12;}
    void paint(Graphics& g)override{g.setColour(Colour(0xff101a27));g.fillRoundedRectangle(getLocalBounds().toFloat(),9);g.setColour(line);g.drawRoundedRectangle(getLocalBounds().toFloat().reduced(.5f),9,.7f);}
    void resized()override{body.setBounds(getLocalBounds());}
    int idealHeight()const{return body.idealHeight();}
};
class Page final : public Component {
public:
    IconButton back{"back"};Label title,brandName;Image brand;Viewport scroll;Stack body,nav;OwnedArray<Component> widgets;std::function<void()> close;int navWidth=0;
    Page(String name,Image logo):brand(logo){setOpaque(true);back.getProperties().set("ghost",true);back.setButtonText("Back");back.onClick=[this]{if(close)close();};title.setText(name,dontSendNotification);title.setFont(FontOptions(21).withStyle("Bold"));brandName.setText("ZLPlay",dontSendNotification);brandName.setFont(FontOptions(22).withStyle("Bold"));for(auto* c:std::array<Component*,5>{&back,&title,&brandName,&scroll,&nav})addAndMakeVisible(c);scroll.setViewedComponent(&body,false);scroll.setScrollBarsShown(true,false);scroll.setScrollBarThickness(5);}
    template<class T,class... A>T& make(A&&... args){auto* result=new T(std::forward<A>(args)...);widgets.add(result);return *result;}
    Label& label(String text,int size=12){auto& l=make<Label>();l.setText(text,dontSendNotification);l.setFont(FontOptions(float(size)));return l;}
    TextButton& button(String label,std::function<void()> action){auto& b=make<TextButton>(label);b.onClick=std::move(action);return b;}
    void refresh(){resized();}
    void paint(Graphics& g)override{g.setGradientFill(ColourGradient(Colour(0xff0e1823),0,0,background,float(getWidth()),float(getHeight()),false));g.fillAll();if(brand.isValid())g.drawImageWithin(brand,36,7,31,34,RectanglePlacement::centred);g.setColour(line);g.drawVerticalLine(180,9,35);}
    void resized()override{auto r=getLocalBounds();auto header=r.removeFromTop(43);back.setBounds(header.removeFromLeft(30));header.removeFromLeft(37);brandName.setBounds(header.removeFromLeft(105));header.removeFromLeft(16);title.setBounds(header);r.removeFromTop(8);if(navWidth>0){nav.setVisible(true);nav.setBounds(r.removeFromLeft(navWidth));r.removeFromLeft(12);}else nav.setVisible(false);scroll.setBounds(r);body.setSize(jmax(1,r.getWidth()-7),jmax(r.getHeight(),body.idealHeight()));body.resized();nav.resized();}
};
class RepertoireRow final : public Component {
public:
    IconButton handle{"list"};TextButton cover,open;Label name,artist,format,key,duration;Image art;bool active=false;int index=0,count=0;std::function<void(int)> move;Point<float> down;bool dragged=false;Viewport* viewport=nullptr;
    RepertoireRow(){for(auto* c:std::array<Component*,8>{&handle,&cover,&open,&name,&artist,&format,&key,&duration})addAndMakeVisible(c);handle.getProperties().set("ghost",true);name.setFont(FontOptions(13).withStyle("Bold"));artist.setFont(FontOptions(11));artist.setColour(Label::textColourId,muted);for(auto* l:{&format,&key,&duration}){l->setFont(FontOptions(11));l->setJustificationType(Justification::centred);}handle.setViewportIgnoreDragFlag(true);handle.addMouseListener(this,false);handle.onClick=[this]{if(dragged)return;PopupMenu menu;menu.addItem(1,String::fromUTF8("↑"),index>0);menu.addItem(2,String::fromUTF8("↓"),index+1<count);Component::SafePointer<RepertoireRow> safe(this);menu.showMenuAsync(PopupMenu::Options().withTargetComponent(&handle),[safe](int id){if(safe&&id>0&&safe->move)safe->move(id==1?-1:1);});};}
    void paint(Graphics& g)override{g.setColour(active?Colour(0xff251f3b):Colour(0xff121b27));g.fillRoundedRectangle(getLocalBounds().toFloat(),8);g.setColour(active?purple:line);g.drawRoundedRectangle(getLocalBounds().toFloat().reduced(.5f),8,active?1.2f:.7f);}
    void resized()override{auto r=getLocalBounds().reduced(10,3);handle.setBounds(r.removeFromLeft(26));open.setBounds(r.removeFromRight(jmin(110,getWidth()/6)).reduced(3,5));cover.setBounds(r.removeFromRight(62).reduced(3,5));duration.setBounds(r.removeFromRight(57));key.setBounds(r.removeFromRight(42));format.setBounds(r.removeFromRight(60));name.setBounds(r.removeFromTop(23));artist.setBounds(r);}
    void mouseDown(const MouseEvent& e)override{down=e.getEventRelativeTo(this).position;dragged=false;}
    void mouseDrag(const MouseEvent& e)override{auto point=e.getEventRelativeTo(this).position;if(std::abs(point.y-down.y)>8)dragged=true;if(viewport){auto at=e.getEventRelativeTo(viewport).position;viewport->autoScroll(int(at.x),int(at.y),24,12);}}
    void mouseUp(const MouseEvent& e)override{if(!dragged||!move)return;int rows=roundToInt((e.getEventRelativeTo(this).position.y-down.y)/57.f);if(rows)move(jlimit(-index,count-1-index,rows));}
};
}
