import base64
import io
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import List

from PySide6.QtCore import Qt, QDate, QRect, QByteArray, QBuffer, QIODevice
from PySide6.QtGui import QColor, QFont, QIcon, QImage, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
    QLabel, QPushButton, QLineEdit, QFrame, QTableWidget, QTableWidgetItem,
    QHeaderView, QAbstractItemView, QDialog, QFormLayout, QDoubleSpinBox,
    QDateEdit, QTextEdit, QFileDialog, QMessageBox, QDialogButtonBox, QComboBox,
    QSplitter, QScrollArea
)

APP_NAME = "دفتر ديون"
APP_VERSION = "1.0"
BACKUP_VERSION = 5
NAVY = "#0B1220"
NAVY2 = "#102A56"
BLUE = "#2563EB"
GOLD = "#F4B942"
GREEN = "#10B981"
RED = "#DC2626"
BG = "#F5F7FB"
MUTED = "#64748B"


def today():
    return QDate.currentDate().toString("yyyy/MM/dd")


def money(value):
    return f"{value:,.0f} د.ع"


def app_data_dir():
    base = os.getenv("APPDATA") or str(Path.home())
    p = Path(base) / "DaftarDeyoon"
    p.mkdir(parents=True, exist_ok=True)
    return p


def documents_dir():
    p = Path.home() / "Documents" / "DaftarDeyoon"
    p.mkdir(parents=True, exist_ok=True)
    return p


def safe_name(text):
    text = re.sub(r'[\\/:*?"<>|]+', "_", text.strip())
    return text[:80] or "customer"


@dataclass
class Payment:
    amount: float
    date: str

    def to_dict(self): return {"amount": self.amount, "date": self.date}


@dataclass
class Addition:
    amount: float
    date: str

    def to_dict(self): return {"amount": self.amount, "date": self.date}


@dataclass
class Transaction:
    type: str
    amount: float
    date: str

    def to_dict(self): return {"type": self.type, "amount": self.amount, "date": self.date}


@dataclass
class Debt:
    id: int
    person: str
    amount: float
    debtDate: str
    paidDate: str = ""
    note: str = ""
    paidAmount: float = 0.0
    payments: List[Payment] = field(default_factory=list)
    phone: str = ""
    location: str = ""
    photoUri: str = ""
    photoData: str = ""
    additions: List[Addition] = field(default_factory=list)
    transactions: List[Transaction] = field(default_factory=list)

    @property
    def remaining(self):
        return max(0.0, self.amount - self.paidAmount)

    @property
    def initial_amount(self):
        return max(0.0, self.amount - sum(a.amount for a in self.additions))

    def ordered_transactions(self):
        if self.transactions:
            return list(self.transactions)
        legacy = [Transaction("payment", p.amount, p.date) for p in self.payments]
        legacy += [Transaction("addition", a.amount, a.date) for a in self.additions]
        return sorted(legacy, key=lambda t: t.date)

    def last_activity(self):
        tx = self.ordered_transactions()
        return tx[-1].date if tx else self.debtDate

    def to_dict(self):
        return {
            "id": self.id,
            "person": self.person,
            "amount": self.amount,
            "debtDate": self.debtDate,
            "paidDate": self.paidDate,
            "note": self.note,
            "paidAmount": self.paidAmount,
            "phone": self.phone,
            "location": self.location,
            "photoUri": self.photoUri,
            "photoData": self.photoData,
            "payments": [p.to_dict() for p in self.payments],
            "additions": [a.to_dict() for a in self.additions],
            "transactions": [t.to_dict() for t in self.ordered_transactions()],
        }

    @staticmethod
    def from_dict(o, fallback_id=0):
        amount = float(o.get("amount", 0) or 0)
        paid_date = str(o.get("paidDate", "") or "")
        paid_amount = o.get("paidAmount", None)
        if paid_amount is None:
            paid_amount = amount if paid_date else 0.0
        return Debt(
            id=int(o.get("id", fallback_id) or fallback_id),
            person=str(o.get("person", "") or "").strip(),
            amount=amount,
            debtDate=str(o.get("debtDate", "") or ""),
            paidDate=paid_date,
            note=str(o.get("note", "") or ""),
            paidAmount=float(paid_amount or 0),
            payments=[Payment(float(p.get("amount", 0) or 0), str(p.get("date", "") or "")) for p in (o.get("payments") or [])],
            phone=str(o.get("phone", "") or ""),
            location=str(o.get("location", "") or ""),
            photoUri=str(o.get("photoUri", "") or ""),
            photoData=str(o.get("photoData", "") or ""),
            additions=[Addition(float(a.get("amount", 0) or 0), str(a.get("date", "") or "")) for a in (o.get("additions") or [])],
            transactions=[Transaction(str(t.get("type", "") or ""), float(t.get("amount", 0) or 0), str(t.get("date", "") or "")) for t in (o.get("transactions") or [])],
        )


class Store:
    def __init__(self):
        self.path = app_data_dir() / "data.json"
        self.previous = app_data_dir() / "data_previous.json"
        self.debts: List[Debt] = []
        self.load()

    def root(self):
        return {
            "version": BACKUP_VERSION,
            "app": "دفتر الديون",
            "updatedAt": int(time.time() * 1000),
            "debts": [d.to_dict() for d in self.debts],
        }

    @staticmethod
    def parse_text(text):
        data = json.loads(text)
        rows = data if isinstance(data, list) else data.get("debts", [])
        if not isinstance(rows, list):
            raise ValueError("صيغة النسخة الاحتياطية غير صحيحة")
        result = []
        now = int(time.time() * 1000)
        for i, row in enumerate(rows):
            if not isinstance(row, dict):
                continue
            d = Debt.from_dict(row, now + i)
            if not d.person or d.amount < 0 or d.paidAmount < 0 or d.paidAmount > d.amount + 0.001:
                raise ValueError("توجد بيانات دين غير صالحة داخل الملف")
            result.append(d)
        return result

    def load(self):
        for path in (self.path, self.previous):
            try:
                if path.exists():
                    self.debts = self.parse_text(path.read_text(encoding="utf-8"))
                    return
            except Exception:
                pass
        self.debts = []

    def save(self):
        if self.path.exists():
            try:
                old = self.path.read_text(encoding="utf-8")
                self.parse_text(old)
                self.previous.write_text(old, encoding="utf-8")
            except Exception:
                pass
        payload = json.dumps(self.root(), ensure_ascii=False, indent=2)
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(payload, encoding="utf-8")
        os.replace(tmp, self.path)
        auto = documents_dir() / "دفتر ديون احتياطي.json"
        auto.write_text(payload, encoding="utf-8")

    def export_backup(self, path):
        Path(path).write_text(json.dumps(self.root(), ensure_ascii=False, indent=2), encoding="utf-8")

    def import_files(self, paths, replace=False):
        imported = []
        for path in paths:
            imported.extend(self.parse_text(Path(path).read_text(encoding="utf-8-sig")))
        if replace:
            merged = imported
        else:
            by_id = {d.id: d for d in self.debts}
            for d in imported:
                by_id[d.id] = d
            merged = list(by_id.values())
        self.debts = sorted(merged, key=lambda d: d.id, reverse=True)
        self.save()
        return len(imported)


def encode_photo(path):
    if not path:
        return ""
    image = QImage(path)
    if image.isNull():
        return ""
    if max(image.width(), image.height()) > 720:
        image = image.scaled(720, 720, Qt.KeepAspectRatio, Qt.SmoothTransformation)
    arr = QByteArray()
    buf = QBuffer(arr)
    buf.open(QIODevice.WriteOnly)
    image.save(buf, "JPG", 82)
    return base64.b64encode(bytes(arr)).decode("ascii")


def photo_pixmap(data, size=72):
    if not data:
        return None
    try:
        raw = base64.b64decode(data)
        pix = QPixmap()
        if pix.loadFromData(raw):
            return pix.scaled(size, size, Qt.KeepAspectRatioByExpanding, Qt.SmoothTransformation)
    except Exception:
        pass
    return None


def render_debt_report(debt: Debt, path):
    txs = debt.ordered_transactions()
    row_h = 86
    note_h = 110 if debt.note else 0
    width = 1240
    height = 640 + (len(txs) + 1) * row_h + note_h
    img = QImage(width, height, QImage.Format_ARGB32)
    img.fill(QColor(BG))
    p = QPainter(img)
    p.setRenderHint(QPainter.Antialiasing)

    def rect(x, y, w, h, color, radius=18):
        p.setPen(Qt.NoPen); p.setBrush(QColor(color)); p.drawRoundedRect(x, y, w, h, radius, radius)

    def text(x, y, w, h, value, size=24, color=NAVY, bold=False, align=Qt.AlignRight | Qt.AlignVCenter):
        f = QFont("Segoe UI", size); f.setBold(bold); p.setFont(f); p.setPen(QColor(color)); p.drawText(QRect(x, y, w, h), align | Qt.TextWordWrap, str(value))

    rect(0, 0, width, 180, NAVY, 0)
    text(60, 32, width-120, 58, APP_NAME, 38, "#FFFFFF", True)
    text(60, 92, width-120, 42, "كشف حساب كامل للزبون", 20, "#CBD5E1")
    rect(70, 152, width-140, 7, GOLD, 4)

    rect(55, 215, width-110, 205, "#FFFFFF", 24)
    text(85, 238, width-170, 55, debt.person, 34, NAVY, True)
    contact = "   |   ".join(x for x in [debt.phone, debt.location] if x)
    if contact: text(85, 296, width-170, 38, contact, 17, MUTED)
    text(85, 342, width-170, 46, f"الدين الكلي: {money(debt.amount)}     المسدد: {money(debt.paidAmount)}     المتبقي: {money(debt.remaining)}", 20, BLUE, True)

    y = 455
    text(70, y, width-140, 48, "سجل العمليات", 27, NAVY, True)
    y += 62

    running_debt = debt.initial_amount
    running_paid = 0.0
    rows = [("original", debt.initial_amount, debt.debtDate)] + [(t.type, t.amount, t.date) for t in txs]
    for i, (kind, amount, date) in enumerate(rows, 1):
        if kind == "addition": running_debt += amount
        elif kind == "payment": running_paid += amount
        balance = max(0.0, running_debt - running_paid)
        rect(55, y, width-110, row_h-8, "#FFFFFF", 16)
        accent = GREEN if kind == "payment" else BLUE
        rect(width-80, y+12, 6, row_h-32, accent, 3)
        label = "الدين الأصلي" if kind == "original" else ("إضافة دين" if kind == "addition" else "تسديد")
        text(80, y+8, 360, 32, f"{i}. {label}", 19, accent, True)
        text(430, y+8, 300, 32, money(amount), 19, NAVY, True)
        text(735, y+8, 210, 32, date, 16, MUTED)
        text(80, y+40, width-160, 30, f"المتبقي بعد العملية: {money(balance)}", 16, MUTED)
        y += row_h

    if debt.note:
        rect(55, y+10, width-110, 90, "#FFFFFF", 16)
        text(80, y+18, width-160, 70, "ملاحظة: " + debt.note, 17, MUTED)
        y += 105

    text(70, height-72, width-140, 42, f"{APP_NAME} • تم إنشاء الكشف من نسخة Windows", 15, MUTED)
    p.end()
    if not img.save(str(path), "PNG"):
        raise RuntimeError("تعذر حفظ الصورة")


def render_summary(debts, path):
    width, height = 1240, 540
    img = QImage(width, height, QImage.Format_ARGB32); img.fill(QColor(BG))
    p = QPainter(img); p.setRenderHint(QPainter.Antialiasing)
    def t(y, s, size, c=NAVY, b=False):
        f=QFont("Segoe UI",size); f.setBold(b); p.setFont(f); p.setPen(QColor(c)); p.drawText(QRect(80,y,width-160,60),Qt.AlignRight|Qt.AlignVCenter,s)
    p.fillRect(0,0,width,170,QColor(NAVY)); t(35,APP_NAME,38,"#FFFFFF",True); t(95,"ملخص جميع الديون",21,"#CBD5E1")
    total=sum(d.amount for d in debts); paid=sum(d.paidAmount for d in debts); rem=sum(d.remaining for d in debts)
    t(215,f"عدد الزبائن: {len(debts)}",25,NAVY,True); t(285,f"إجمالي الديون: {money(total)}",23,BLUE,True); t(345,f"إجمالي المسدد: {money(paid)}",23,GREEN,True); t(405,f"إجمالي المتبقي: {money(rem)}",23,RED,True)
    p.end(); img.save(str(path),"PNG")


class CustomerDialog(QDialog):
    def __init__(self, parent=None, debt=None):
        super().__init__(parent)
        self.setWindowTitle("تعديل بيانات الزبون" if debt else "إضافة زبون ودين")
        self.setMinimumWidth(520)
        self.photo_path = ""
        lay = QVBoxLayout(self); form = QFormLayout(); form.setLabelAlignment(Qt.AlignRight)
        self.person=QLineEdit(debt.person if debt else ""); self.phone=QLineEdit(debt.phone if debt else ""); self.location=QLineEdit(debt.location if debt else "")
        self.amount=QDoubleSpinBox(); self.amount.setMaximum(999999999999); self.amount.setDecimals(0); self.amount.setValue(debt.initial_amount if debt else 0); self.amount.setSuffix(" د.ع"); self.amount.setEnabled(debt is None)
        self.date=QDateEdit(); self.date.setCalendarPopup(True); self.date.setDisplayFormat("yyyy/MM/dd")
        qd=QDate.fromString(debt.debtDate,"yyyy/MM/dd") if debt else QDate.currentDate(); self.date.setDate(qd if qd.isValid() else QDate.currentDate()); self.date.setEnabled(debt is None)
        self.note=QTextEdit(debt.note if debt else ""); self.note.setMaximumHeight(90)
        self.photo_btn=QPushButton("اختيار صورة للزبون"); self.photo_btn.clicked.connect(self.choose_photo)
        form.addRow("الاسم *",self.person); form.addRow("رقم الهاتف",self.phone); form.addRow("الموقع",self.location); form.addRow("مبلغ الدين *",self.amount); form.addRow("تاريخ الدين",self.date); form.addRow("ملاحظة",self.note); form.addRow("الصورة",self.photo_btn)
        lay.addLayout(form)
        box=QDialogButtonBox(QDialogButtonBox.Save|QDialogButtonBox.Cancel); box.button(QDialogButtonBox.Save).setText("حفظ"); box.button(QDialogButtonBox.Cancel).setText("إلغاء"); box.accepted.connect(self.validate); box.rejected.connect(self.reject); lay.addWidget(box)
    def choose_photo(self):
        path,_=QFileDialog.getOpenFileName(self,"اختيار صورة","","الصور (*.png *.jpg *.jpeg *.webp)")
        if path: self.photo_path=path; self.photo_btn.setText(Path(path).name)
    def validate(self):
        if not self.person.text().strip(): QMessageBox.warning(self,"تنبيه","اكتب اسم الزبون"); return
        if self.amount.isEnabled() and self.amount.value()<=0: QMessageBox.warning(self,"تنبيه","اكتب مبلغ دين صحيح"); return
        self.accept()


class TxDialog(QDialog):
    def __init__(self, title, max_amount=None, parent=None):
        super().__init__(parent); self.setWindowTitle(title); self.setMinimumWidth(420)
        lay=QFormLayout(self); self.amount=QDoubleSpinBox(); self.amount.setDecimals(0); self.amount.setMaximum(max_amount if max_amount is not None else 999999999999); self.amount.setSuffix(" د.ع"); self.amount.setValue(0)
        self.date=QDateEdit(QDate.currentDate()); self.date.setCalendarPopup(True); self.date.setDisplayFormat("yyyy/MM/dd")
        lay.addRow("المبلغ",self.amount); lay.addRow("التاريخ",self.date)
        box=QDialogButtonBox(QDialogButtonBox.Ok|QDialogButtonBox.Cancel); box.button(QDialogButtonBox.Ok).setText("تأكيد"); box.button(QDialogButtonBox.Cancel).setText("إلغاء"); box.accepted.connect(self.validate); box.rejected.connect(self.reject); lay.addRow(box)
    def validate(self):
        if self.amount.value()<=0: QMessageBox.warning(self,"تنبيه","أدخل مبلغ صحيح"); return
        self.accept()


class DebtDialog(QDialog):
    def __init__(self, main, debt):
        super().__init__(main); self.main=main; self.debt=debt; self.setWindowTitle("تفاصيل الزبون - "+debt.person); self.resize(900,700)
        root=QVBoxLayout(self)
        top=QHBoxLayout(); self.name=QLabel(); self.name.setObjectName("detailName"); self.status=QLabel(); self.status.setObjectName("statusPill"); top.addWidget(self.name,1); top.addWidget(self.status); root.addLayout(top)
        self.info=QLabel(); self.info.setObjectName("muted"); root.addWidget(self.info)
        cards=QHBoxLayout(); self.total=MoneyCard("إجمالي الدين",BLUE); self.paid=MoneyCard("المسدد",GREEN); self.rem=MoneyCard("المتبقي",RED); cards.addWidget(self.total); cards.addWidget(self.paid); cards.addWidget(self.rem); root.addLayout(cards)
        actions=QHBoxLayout();
        for label,slot,obj in [("تسجيل تسديد",self.add_payment,"primary"),("إضافة دين",self.add_debt,"gold"),("تعديل البيانات",self.edit,"secondary"),("تصدير صورة",self.export,"secondary"),("حذف الزبون",self.delete,"danger")]:
            b=QPushButton(label); b.setObjectName(obj); b.clicked.connect(slot); actions.addWidget(b)
        root.addLayout(actions)
        self.table=QTableWidget(0,5); self.table.setHorizontalHeaderLabels(["العملية","المبلغ","التاريخ","المتبقي بعد العملية","#"]); self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch); self.table.setEditTriggers(QAbstractItemView.NoEditTriggers); self.table.setSelectionBehavior(QAbstractItemView.SelectRows); root.addWidget(self.table,1)
        self.note=QLabel(); self.note.setWordWrap(True); self.note.setObjectName("noteBox"); root.addWidget(self.note); self.refresh()
    def refresh(self):
        d=self.debt; self.name.setText(d.person); self.info.setText("   |   ".join(x for x in [d.phone,d.location,"تاريخ الدين: "+d.debtDate] if x)); self.total.set_value(d.amount); self.paid.set_value(d.paidAmount); self.rem.set_value(d.remaining); self.status.setText("مُسدّد بالكامل" if d.remaining<=0 else "متبقي دين"); self.status.setProperty("paid",d.remaining<=0); self.status.style().unpolish(self.status); self.status.style().polish(self.status); self.note.setText("ملاحظة: "+d.note if d.note else "")
        tx=d.ordered_transactions(); self.table.setRowCount(len(tx)+1); running_debt=d.initial_amount; running_paid=0.0
        self._set_row(0,"الدين الأصلي",d.initial_amount,d.debtDate,running_debt,1)
        for i,t in enumerate(tx,1):
            if t.type=="addition": running_debt+=t.amount; label="إضافة دين"
            else: running_paid+=t.amount; label="تسديد"
            self._set_row(i,label,t.amount,t.date,max(0,running_debt-running_paid),i+1)
    def _set_row(self,r,label,amount,date,balance,num):
        vals=[label,money(amount),date,money(balance),str(num)]
        for c,v in enumerate(vals):
            item=QTableWidgetItem(v); item.setTextAlignment(Qt.AlignCenter); self.table.setItem(r,c,item)
    def add_payment(self):
        if self.debt.remaining<=0: QMessageBox.information(self,"مُسدّد","هذا الدين مسدد بالكامل"); return
        dlg=TxDialog("تسجيل تسديد",self.debt.remaining,self)
        if dlg.exec()!=QDialog.Accepted:return
        a=dlg.amount.value(); date=dlg.date.date().toString("yyyy/MM/dd"); self.debt.paidAmount+=a; self.debt.payments.append(Payment(a,date)); self.debt.transactions.append(Transaction("payment",a,date)); self.debt.paidDate=date; self.main.changed(); self.refresh()
    def add_debt(self):
        dlg=TxDialog("إضافة دين",None,self)
        if dlg.exec()!=QDialog.Accepted:return
        a=dlg.amount.value(); date=dlg.date.date().toString("yyyy/MM/dd"); self.debt.amount+=a; self.debt.additions.append(Addition(a,date)); self.debt.transactions.append(Transaction("addition",a,date)); self.main.changed(); self.refresh()
    def edit(self):
        dlg=CustomerDialog(self,self.debt)
        if dlg.exec()!=QDialog.Accepted:return
        self.debt.person=dlg.person.text().strip(); self.debt.phone=dlg.phone.text().strip(); self.debt.location=dlg.location.text().strip(); self.debt.note=dlg.note.toPlainText().strip();
        if dlg.photo_path:self.debt.photoData=encode_photo(dlg.photo_path); self.debt.photoUri=""
        self.main.changed(); self.setWindowTitle("تفاصيل الزبون - "+self.debt.person); self.refresh()
    def export(self):
        path,_=QFileDialog.getSaveFileName(self,"تصدير كشف الحساب",str(Path.home()/"Desktop"/(safe_name(self.debt.person)+".png")),"PNG (*.png)")
        if path:
            try: render_debt_report(self.debt,path); QMessageBox.information(self,"تم","تم تصدير كشف الحساب بنجاح")
            except Exception as e: QMessageBox.critical(self,"خطأ",str(e))
    def delete(self):
        if QMessageBox.question(self,"تأكيد الحذف","حذف الزبون وكل عملياته؟",QMessageBox.Yes|QMessageBox.No)!=QMessageBox.Yes:return
        self.main.store.debts=[d for d in self.main.store.debts if d.id!=self.debt.id]; self.main.changed(); self.accept()


class MoneyCard(QFrame):
    def __init__(self,title,color):
        super().__init__(); self.setObjectName("moneyCard"); lay=QVBoxLayout(self); self.t=QLabel(title); self.t.setObjectName("muted"); self.v=QLabel("0 د.ع"); self.v.setStyleSheet(f"font-size:22px;font-weight:800;color:{color};"); lay.addWidget(self.t); lay.addWidget(self.v)
    def set_value(self,v): self.v.setText(money(v))


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__(); self.store=Store(); self.setWindowTitle(APP_NAME); self.setMinimumSize(1050,680); self.resize(1280,780)
        central=QWidget(); self.setCentralWidget(central); root=QHBoxLayout(central); root.setContentsMargins(0,0,0,0); root.setSpacing(0)
        side=QFrame(); side.setObjectName("sidebar"); side.setFixedWidth(230); sl=QVBoxLayout(side); sl.setContentsMargins(22,28,22,24)
        brand=QLabel(APP_NAME); brand.setObjectName("brand"); sub=QLabel("إدارة ديون الزبائن\nبسهولة واحترافية"); sub.setObjectName("sideMuted"); sl.addWidget(brand); sl.addWidget(sub); sl.addSpacing(28)
        for text,slot in [("+ إضافة زبون",self.add_customer),("نسخة احتياطية",self.backup),("استيراد نسخة",self.import_backup),("تصدير الكل",self.export_all)]:
            b=QPushButton(text); b.setObjectName("sideButton"); b.clicked.connect(slot); sl.addWidget(b)
        sl.addStretch(); ver=QLabel("Windows • v"+APP_VERSION+"\nبياناتك محفوظة على جهازك"); ver.setObjectName("sideMuted"); sl.addWidget(ver); root.addWidget(side)
        body=QWidget(); bl=QVBoxLayout(body); bl.setContentsMargins(28,24,28,24); bl.setSpacing(16)
        head=QHBoxLayout(); titleBox=QVBoxLayout(); title=QLabel("لوحة الديون"); title.setObjectName("pageTitle"); hint=QLabel("تابع كل الزبائن والعمليات من مكان واحد"); hint.setObjectName("muted"); titleBox.addWidget(title); titleBox.addWidget(hint); head.addLayout(titleBox,1); self.search=QLineEdit(); self.search.setPlaceholderText("ابحث بالاسم أو الهاتف..."); self.search.setClearButtonEnabled(True); self.search.setFixedWidth(320); self.search.textChanged.connect(self.refresh); head.addWidget(self.search); bl.addLayout(head)
        cards=QHBoxLayout(); self.c_total=MoneyCard("إجمالي الديون",BLUE); self.c_paid=MoneyCard("إجمالي المسدد",GREEN); self.c_rem=MoneyCard("إجمالي المتبقي",RED); self.c_count=MoneyCard("عدد الزبائن",GOLD); cards.addWidget(self.c_total); cards.addWidget(self.c_paid); cards.addWidget(self.c_rem); cards.addWidget(self.c_count); bl.addLayout(cards)
        filterRow=QHBoxLayout(); lab=QLabel("الزبائن"); lab.setObjectName("sectionTitle"); filterRow.addWidget(lab); filterRow.addStretch(); self.filter=QComboBox(); self.filter.addItems(["الكل","عليهم دين","مسدد بالكامل"]); self.filter.currentIndexChanged.connect(self.refresh); filterRow.addWidget(self.filter); bl.addLayout(filterRow)
        self.table=QTableWidget(0,7); self.table.setHorizontalHeaderLabels(["الاسم","الهاتف","إجمالي الدين","المسدد","المتبقي","آخر حركة","الحالة"]); self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch); self.table.setEditTriggers(QAbstractItemView.NoEditTriggers); self.table.setSelectionBehavior(QAbstractItemView.SelectRows); self.table.setAlternatingRowColors(False); self.table.verticalHeader().setVisible(False); self.table.doubleClicked.connect(self.open_selected); bl.addWidget(self.table,1)
        foot=QLabel("دبل كلك على أي زبون لعرض سجل العمليات الكامل • النسخ الاحتياطية متوافقة مع تطبيق الهاتف"); foot.setObjectName("muted"); bl.addWidget(foot); root.addWidget(body,1)
        self.refresh()
    def changed(self): self.store.save(); self.refresh()
    def refresh(self):
        ds=self.store.debts; self.c_total.set_value(sum(d.amount for d in ds)); self.c_paid.set_value(sum(d.paidAmount for d in ds)); self.c_rem.set_value(sum(d.remaining for d in ds)); self.c_count.v.setText(str(len(ds)))
        q=self.search.text().strip().lower(); mode=self.filter.currentIndex(); rows=[]
        for d in ds:
            if q and q not in d.person.lower() and q not in d.phone.lower(): continue
            if mode==1 and d.remaining<=0: continue
            if mode==2 and d.remaining>0: continue
            rows.append(d)
        rows.sort(key=lambda d:(d.remaining<=0,-d.id)); self.table.setRowCount(len(rows)); self._visible=rows
        for r,d in enumerate(rows):
            vals=[d.person,d.phone,money(d.amount),money(d.paidAmount),money(d.remaining),d.last_activity(),"مسدد" if d.remaining<=0 else "متبقي"]
            for c,v in enumerate(vals):
                it=QTableWidgetItem(v); it.setTextAlignment(Qt.AlignCenter if c else Qt.AlignRight|Qt.AlignVCenter); it.setData(Qt.UserRole,d.id); self.table.setItem(r,c,it)
    def selected_debt(self):
        r=self.table.currentRow();
        if r<0 or r>=len(getattr(self,"_visible",[])): return None
        return self._visible[r]
    def open_selected(self,*_):
        d=self.selected_debt();
        if d: DebtDialog(self,d).exec(); self.refresh()
    def add_customer(self):
        dlg=CustomerDialog(self)
        if dlg.exec()!=QDialog.Accepted:return
        photo=encode_photo(dlg.photo_path); d=Debt(int(time.time()*1000),dlg.person.text().strip(),dlg.amount.value(),dlg.date.date().toString("yyyy/MM/dd"),note=dlg.note.toPlainText().strip(),phone=dlg.phone.text().strip(),location=dlg.location.text().strip(),photoData=photo)
        self.store.debts.insert(0,d); self.changed(); DebtDialog(self,d).exec()
    def backup(self):
        if not self.store.debts: QMessageBox.information(self,"لا توجد بيانات","أضف زبائن أولاً"); return
        default=str(Path.home()/"Downloads"/"دفتر ديون احتياطي.json"); path,_=QFileDialog.getSaveFileName(self,"حفظ نسخة احتياطية",default,"JSON (*.json)")
        if path:
            try:self.store.export_backup(path); QMessageBox.information(self,"تم","تم حفظ نسخة متوافقة مع تطبيق الهاتف والحاسبة")
            except Exception as e: QMessageBox.critical(self,"خطأ",str(e))
    def import_backup(self):
        paths,_=QFileDialog.getOpenFileNames(self,"استيراد نسخة احتياطية","","JSON (*.json)")
        if not paths:return
        box=QMessageBox(self); box.setWindowTitle("طريقة الاستيراد"); box.setText("تريد دمج النسخة مع البيانات الحالية لو استبدالها بالكامل؟"); merge=box.addButton("دمج",QMessageBox.AcceptRole); replace=box.addButton("استبدال الكل",QMessageBox.DestructiveRole); box.addButton("إلغاء",QMessageBox.RejectRole); box.exec(); clicked=box.clickedButton()
        if clicked not in (merge,replace):return
        try:
            n=self.store.import_files(paths,replace=clicked==replace); self.refresh(); QMessageBox.information(self,"تم",f"تم استيراد {n} سجل بنجاح")
        except Exception as e: QMessageBox.critical(self,"تعذر الاستيراد",str(e))
    def export_all(self):
        if not self.store.debts: QMessageBox.information(self,"لا توجد بيانات","ماكو زبائن للتصدير"); return
        folder=QFileDialog.getExistingDirectory(self,"اختيار مجلد التصدير",str(Path.home()/"Pictures"))
        if not folder:return
        try:
            base=Path(folder)/("دفتر ديون - تصدير كامل - "+QDate.currentDate().toString("yyyy-MM-dd")); base.mkdir(parents=True,exist_ok=True); render_summary(self.store.debts,base/"00 - ملخص الديون.png")
            errors=[]
            for i,d in enumerate(self.store.debts,1):
                try: render_debt_report(d,base/f"{i:03d} - {safe_name(d.person)}.png")
                except Exception as e: errors.append(d.person)
            msg=f"تم تصدير {len(self.store.debts)-len(errors)} زبون داخل:\n{base}"
            if errors: msg+=f"\n\nتعذر تصدير {len(errors)} زبون فقط، وباقي الصور تم حفظها."
            QMessageBox.information(self,"اكتمل التصدير",msg)
        except Exception as e: QMessageBox.critical(self,"خطأ",str(e))


def stylesheet():
    return f"""
    QWidget {{ font-family:'Segoe UI'; font-size:14px; color:{NAVY}; }}
    QMainWindow, QWidget {{ background:{BG}; }}
    #sidebar {{ background:{NAVY}; border:0; }}
    #brand {{ color:white; font-size:28px; font-weight:800; }}
    #sideMuted {{ color:#94A3B8; font-size:12px; }}
    #sideButton {{ background:#13233D; color:white; border:1px solid #1E3354; border-radius:12px; padding:12px; text-align:right; font-weight:650; }}
    #sideButton:hover {{ background:{NAVY2}; border-color:{BLUE}; }}
    #pageTitle {{ font-size:27px; font-weight:800; }} #sectionTitle {{ font-size:19px; font-weight:750; }} #detailName {{ font-size:28px; font-weight:800; }}
    #muted {{ color:{MUTED}; }}
    #moneyCard {{ background:white; border:1px solid #E2E8F0; border-radius:16px; min-height:78px; }}
    QLineEdit,QDoubleSpinBox,QDateEdit,QTextEdit,QComboBox {{ background:white; border:1px solid #CBD5E1; border-radius:10px; padding:9px; min-height:20px; selection-background-color:{BLUE}; }}
    QLineEdit:focus,QDoubleSpinBox:focus,QDateEdit:focus,QTextEdit:focus,QComboBox:focus {{ border:1px solid {BLUE}; }}
    QTableWidget {{ background:white; border:1px solid #E2E8F0; border-radius:14px; gridline-color:#EEF2F7; selection-background-color:#EAF2FF; selection-color:{NAVY}; }}
    QHeaderView::section {{ background:{NAVY}; color:white; padding:10px; border:0; font-weight:700; }}
    QTableWidget::item {{ padding:8px; }}
    QPushButton {{ border:0; border-radius:10px; padding:10px 15px; font-weight:700; background:#E2E8F0; }}
    QPushButton#primary {{ background:{BLUE}; color:white; }} QPushButton#gold {{ background:{GOLD}; color:{NAVY}; }} QPushButton#danger {{ background:#FEECEC; color:{RED}; }} QPushButton#secondary {{ background:white; border:1px solid #CBD5E1; }}
    #statusPill {{ background:#FEF3C7; color:#92400E; padding:7px 12px; border-radius:10px; font-weight:700; }}
    #statusPill[paid="true"] {{ background:#D1FAE5; color:#047857; }}
    #noteBox {{ background:white; border:1px solid #E2E8F0; border-radius:12px; padding:10px; color:{MUTED}; }}
    QScrollBar:vertical {{ width:10px; background:transparent; }} QScrollBar::handle:vertical {{ background:#CBD5E1; border-radius:5px; min-height:30px; }}
    """


def self_test():
    d=Debt(1,"اختبار",100000,"2026/10/02")
    d.additions.append(Addition(25000,"2026/10/03")); d.transactions.append(Transaction("addition",25000,"2026/10/03")); d.amount+=25000
    d.payments.append(Payment(40000,"2026/10/04")); d.transactions.append(Transaction("payment",40000,"2026/10/04")); d.paidAmount+=40000
    root={"version":5,"app":"دفتر الديون","updatedAt":1,"debts":[d.to_dict()]}; parsed=Store.parse_text(json.dumps(root,ensure_ascii=False))
    assert len(parsed)==1 and parsed[0].remaining==85000 and parsed[0].initial_amount==100000 and len(parsed[0].ordered_transactions())==2
    legacy=[{"id":2,"person":"قديم","amount":50000,"debtDate":"2026/01/01","paidDate":"","payments":[],"additions":[],"transactions":[]}]
    assert Store.parse_text(json.dumps(legacy))[0].remaining==50000
    print("SELF_TEST_OK")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test(); raise SystemExit(0)
    app=QApplication(sys.argv); app.setApplicationName(APP_NAME); app.setApplicationVersion(APP_VERSION); app.setLayoutDirection(Qt.RightToLeft); app.setStyle("Fusion"); app.setStyleSheet(stylesheet())
    icon_path=Path(__file__).with_name("app.ico")
    if icon_path.exists(): app.setWindowIcon(QIcon(str(icon_path)))
    w=MainWindow(); w.show(); sys.exit(app.exec())
