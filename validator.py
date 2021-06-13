# This is a sample Python script.

# Press Shift+F10 to execute it or replace it with your code.
# Press Double Shift to search everywhere for classes, files, tool windows, actions, and settings.
import sys


def main():
    if len(sys.argv) < 2:
        sys.exit(-1)

    for i in range(1, len(sys.argv) - 1):
        filename1 = sys.argv[i]
        filename2 = sys.argv[i + 1]
        file1 = open(filename1, "r")
        file2 = open(filename2, "r")

        count = 0
        for line1 in file1:
            line2 = file2.readline()
            count += 1

            if line1 != line2:
                # remove tabs
                line1 = line1[1:]
                line2 = line2[1:]

                if line1 != line2:
                    # removes more whitespace
                    line1 = line1[3:]

                if line1 != line2:
                    print("line ", count)
                    print("eg: ", line1, end="")
                    print("my: ", line2)

        print('ok')


# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    main()
